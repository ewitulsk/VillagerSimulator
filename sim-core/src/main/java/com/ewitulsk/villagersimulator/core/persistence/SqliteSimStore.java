package com.ewitulsk.villagersimulator.core.persistence;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;
import com.ewitulsk.villagersimulator.core.SimWorld;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * One SQLite database per world ({@code world/villagersimulator/sim.db}, docs/ARCHITECTURE.md §17). Bulk state is a
 * blob per shard; notable events go into an indexed table. Each save is one transaction, so a crash never leaves a
 * half-written save. Not thread-safe: use from one thread (the save thread).
 */
public final class SqliteSimStore implements AutoCloseable {
    /** @param forgotten saved event records older than the retention, left in the database */
    public record Loaded(long time, byte[] data, List<EventRecord> events, int forgotten) {}

    private final Connection connection;
    private volatile int savedEvents;

    private SqliteSimStore(Connection connection) throws SQLException {
        this.connection = connection;
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS shard_snapshot (shard INTEGER PRIMARY KEY, format INTEGER NOT NULL, "
                    + "sim_time INTEGER NOT NULL, data BLOB NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS event_log (id INTEGER PRIMARY KEY, time INTEGER NOT NULL, "
                    + "type TEXT NOT NULL, actor INTEGER NOT NULL, cause INTEGER NOT NULL, witnesses TEXT NOT NULL, "
                    + "detail TEXT NOT NULL)");
            try {
                s.execute("ALTER TABLE event_log ADD COLUMN seq INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException alreadyThere) {
                // the column exists
            }
            s.execute("CREATE INDEX IF NOT EXISTS event_log_type ON event_log (type, time)");
            s.execute("CREATE INDEX IF NOT EXISTS event_log_actor ON event_log (actor, time)");
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM event_log")) {
                savedEvents = r.next() ? r.getInt(1) : 0;
            }
        }
        connection.setAutoCommit(false);
    }

    public static SqliteSimStore open(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            SQLiteConfig config = new SQLiteConfig();
            config.setJournalMode(SQLiteConfig.JournalMode.WAL);
            config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
            SQLiteDataSource ds = new SQLiteDataSource(config);
            ds.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
            return new SqliteSimStore(ds.getConnection());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException e) {
            throw new IllegalStateException("Can't open " + file, e);
        }
    }

    /** How many event records are saved; pass it to {@link SimWorld#snapshot(int)}. */
    public int savedEvents() {
        return savedEvents;
    }

    public void save(SimWorld.Snapshot snapshot, int format) {
        try {
            try (PreparedStatement p = connection.prepareStatement(
                    "INSERT OR REPLACE INTO shard_snapshot (shard, format, sim_time, data) VALUES (0, ?, ?, ?)")) {
                p.setInt(1, format);
                p.setLong(2, snapshot.time());
                p.setBytes(3, snapshot.data());
                p.executeUpdate();
            }
            try (PreparedStatement p = connection.prepareStatement(
                    "INSERT OR IGNORE INTO event_log (id, time, type, actor, cause, witnesses, detail, seq) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                int seq = savedEvents;
                for (EventRecord r : snapshot.newEvents()) {
                    p.setLong(1, r.id());
                    p.setLong(2, r.time());
                    p.setString(3, r.type().toString());
                    p.setInt(4, r.actor().raw());
                    p.setLong(5, r.cause());
                    p.setString(6, witnesses(r.witnesses()));
                    p.setString(7, r.detail());
                    p.setInt(8, ++seq);
                    p.addBatch();
                }
                p.executeBatch();
                connection.commit();
                savedEvents = seq;
            }
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // the original error is the interesting one
            }
            throw new IllegalStateException("Saving the sim failed", e);
        }
    }

    public Optional<Loaded> load() {
        return load(Long.MAX_VALUE);
    }

    /** Loads the snapshot and the event records from the last {@code retention} ticks before it. */
    public Optional<Loaded> load(long retention) {
        try {
            byte[] data;
            long time;
            try (Statement s = connection.createStatement();
                 ResultSet r = s.executeQuery("SELECT sim_time, data FROM shard_snapshot WHERE shard = 0")) {
                if (!r.next()) return Optional.empty();
                time = r.getLong(1);
                data = r.getBytes(2);
            }
            List<EventRecord> events = new ArrayList<>();
            long cutoff = retention == Long.MAX_VALUE ? Long.MIN_VALUE : time - retention;
            int forgotten;
            try (PreparedStatement p = connection.prepareStatement("SELECT COUNT(*) FROM event_log WHERE time < ?")) {
                p.setLong(1, cutoff);
                try (ResultSet r = p.executeQuery()) {
                    forgotten = r.next() ? r.getInt(1) : 0;
                }
            }
            // Times never decrease along the log, so the old records are exactly the first `forgotten`.
            try (PreparedStatement p = connection.prepareStatement(
                    "SELECT id, time, type, actor, cause, witnesses, detail FROM event_log ORDER BY seq, id LIMIT -1 OFFSET ?")) {
                p.setInt(1, forgotten);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        events.add(new EventRecord(r.getLong(1), r.getLong(2), Id.parse(r.getString(3)),
                                new EntityId(r.getInt(4)), r.getLong(5), parseWitnesses(r.getString(6)), r.getString(7)));
                    }
                }
            }
            connection.commit();
            return Optional.of(new Loaded(time, data, events, forgotten));
        } catch (SQLException e) {
            throw new IllegalStateException("Loading the sim failed", e);
        }
    }

    private static String witnesses(List<EntityId> list) {
        StringBuilder b = new StringBuilder();
        for (EntityId e : list) {
            if (!b.isEmpty()) b.append(',');
            b.append(e.raw());
        }
        return b.toString();
    }

    private static List<EntityId> parseWitnesses(String text) {
        if (text.isEmpty()) return List.of();
        return Arrays.stream(text.split(",")).map(s -> new EntityId(Integer.parseInt(s))).toList();
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
