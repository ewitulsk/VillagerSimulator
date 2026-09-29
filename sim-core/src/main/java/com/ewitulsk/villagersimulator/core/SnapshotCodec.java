package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;
import com.ewitulsk.villagersimulator.core.storage.DenseStore;
import com.ewitulsk.villagersimulator.core.storage.Scheduler;
import com.ewitulsk.villagersimulator.core.storage.SparseStore;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Binary world snapshot. Every component is its own length-prefixed section keyed by component id, with dense fields
 * matched by name, so adding/removing fields or components is a safe change. Sections no registered component
 * claims are kept and written back unchanged (docs/ARCHITECTURE.md §17).
 *
 * <p>Since format 3 the snapshot is deflate-compressed behind a {@code VSZ0} header (sparse components are JSON, which
 * compresses about tenfold), and component sections are encoded in parallel (docs/ROADMAP.md Phase 6).
 */
final class SnapshotCodec {
    private static final Logger LOG = LoggerFactory.getLogger("VillagerSim/Save");
    private static final int MAGIC = 0x56534D30; // "VSM0"
    private static final int MAGIC_COMPRESSED = 0x56535A30; // "VSZ0"
    /** 2: per-shard queues and shard assignments. 3: compressed. */
    static final int FORMAT = 3;
    private static final byte DENSE = 1;
    private static final byte SPARSE = 2;
    private static final byte GRAPH = 3;
    private static final byte SHARDS = 4;
    private static final Id SHARD_SECTION = Id.of("villagersimulator", "shards");
    private static final Id RELATIONSHIPS = Id.of("villagersimulator", "relationships");

    private SnapshotCodec() {}

    static byte[] write(SimWorld w) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 20);
            new DataOutputStream(bytes).writeInt(MAGIC_COMPRESSED);
            java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED);
            DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(
                    new java.util.zip.DeflaterOutputStream(bytes, deflater, 1 << 16), 1 << 16));
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeLong(w.now());
            out.writeLong(0); // per-shard sequence numbers are restored from the tasks themselves

            int limit = w.entities.indexLimit();
            out.writeInt(limit);
            for (int i = 0; i < limit; i++) {
                out.writeByte(w.entities.generation(i));
                out.writeBoolean(w.entities.aliveAt(i));
            }
            int[] free = w.entities.freeList();
            out.writeInt(free.length);
            for (int i : free) out.writeInt(i);

            List<byte[]> sections = new ArrayList<>();
            ByteArrayOutputStream shardBytes = new ByteArrayOutputStream();
            DataOutputStream sb = new DataOutputStream(shardBytes);
            sb.writeInt(w.queues.size());
            sb.writeInt(limit);
            for (int i = 0; i < limit; i++) sb.writeInt(i < w.shardOf.length ? w.shardOf[i] : 0);
            sb.flush();
            sections.add(section(SHARD_SECTION, SHARDS, shardBytes.toByteArray()));
            // Components are independent, so encode them in parallel (the world isn't running meanwhile).
            sections.addAll(w.dense.values().parallelStream()
                    .map(s -> unchecked(() -> section(s.component().id(), DENSE, writeDense(s)))).toList());
            sections.addAll(w.sparse.values().parallelStream()
                    .map(s -> unchecked(() -> section(s.component().id(), SPARSE, writeSparse(s)))).toList());
            ByteArrayOutputStream graph = new ByteArrayOutputStream();
            DataOutputStream g = new DataOutputStream(graph);
            w.relationships.write(g);
            g.flush();
            sections.add(section(RELATIONSHIPS, GRAPH, graph.toByteArray()));
            sections.addAll(w.unknownSections.values());
            out.writeInt(sections.size());
            for (byte[] s : sections) out.write(s);

            List<Scheduler.Task> tasks = new ArrayList<>();
            for (Scheduler q : w.queues) tasks.addAll(q.ordered());
            out.writeInt(tasks.size());
            for (Scheduler.Task t : tasks) {
                out.writeLong(t.time());
                out.writeInt(t.priority());
                out.writeLong(t.seq());
                writeString(out, t.type().id().toString());
                out.writeInt(t.target());
                out.writeLong(t.arg());
            }
            out.close();
            deflater.end();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    private static <T> T unchecked(IoSupplier<T> s) {
        try {
            return s.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void read(SimWorld w, byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            int magic = in.readInt();
            if (magic == MAGIC_COMPRESSED) {
                in = new DataInputStream(new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(
                        new ByteArrayInputStream(data, 4, data.length - 4)), 1 << 16));
                magic = in.readInt();
            }
            if (magic != MAGIC) throw new IllegalArgumentException("Not a Villager Simulator snapshot");
            int format = in.readInt();
            if (format > FORMAT) throw new IllegalArgumentException("Snapshot format " + format + " is newer than " + FORMAT);
            w.setNow(in.readLong());
            in.readLong(); // format 1's global sequence number; format 2 restores per shard

            int limit = in.readInt();
            byte[] generations = new byte[limit];
            boolean[] alive = new boolean[limit];
            for (int i = 0; i < limit; i++) {
                generations[i] = in.readByte();
                alive[i] = in.readBoolean();
            }
            int[] free = new int[in.readInt()];
            for (int i = 0; i < free.length; i++) free[i] = in.readInt();
            w.entities.restore(limit, generations, alive, free);

            int sections = in.readInt();
            for (int s = 0; s < sections; s++) {
                Id id = Id.parse(readString(in));
                byte kind = in.readByte();
                byte[] payload = new byte[in.readInt()];
                in.readFully(payload);
                DataInputStream p = new DataInputStream(new ByteArrayInputStream(payload));
                if (kind == DENSE && w.dense.containsKey(id)) readDense(w.dense.get(id), p);
                else if (kind == SPARSE && w.sparse.containsKey(id)) readSparse(w.sparse.get(id), p);
                else if (kind == GRAPH && id.equals(RELATIONSHIPS)) w.relationships.read(p);
                else if (kind == SHARDS && id.equals(SHARD_SECTION)) {
                    int count = p.readInt();
                    int n = p.readInt();
                    int[] assignment = new int[n];
                    for (int i = 0; i < n; i++) assignment[i] = p.readInt();
                    w.restoreShards(count, assignment);
                }
                else {
                    LOG.warn("Keeping unknown save section {} (no registered component claims it)", id);
                    w.unknownSections.put(id, section(id, kind, payload));
                }
            }

            int taskCount = in.readInt();
            Map<Id, TaskType> types = w.tasks;
            for (int i = 0; i < taskCount; i++) {
                long time = in.readLong();
                int priority = in.readInt();
                long seq = in.readLong();
                Id type = Id.parse(readString(in));
                int target = in.readInt();
                long arg = in.readLong();
                TaskType t = types.get(type);
                if (t == null) {
                    LOG.warn("Dropping saved task of unknown type {}", type);
                    continue;
                }
                int shard = target == 0 ? 0 : w.shardIndex(new com.ewitulsk.villagersimulator.api.sim.EntityId(target));
                w.queues.get(shard).restore(new Scheduler.Task(time, priority, seq, t, target, arg));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] section(Id id, byte kind, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeString(out, id.toString());
        out.writeByte(kind);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] writeDense(DenseStore s) throws IOException {
        DenseComponent c = s.component();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(c.version());
        writeNames(out, c.floatNames());
        writeNames(out, c.longNames());
        writeNames(out, c.intNames());
        out.writeInt(s.present().cardinality());
        for (int i = s.present().nextSetBit(0); i >= 0; i = s.present().nextSetBit(i + 1)) {
            out.writeInt(i);
            for (int f = 0; f < c.floatNames().size(); f++) out.writeFloat(s.getFloat(f, i));
            for (int f = 0; f < c.longNames().size(); f++) out.writeLong(s.getLong(f, i));
            for (int f = 0; f < c.intNames().size(); f++) out.writeInt(s.getInt(f, i));
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static void readDense(DenseStore s, DataInputStream in) throws IOException {
        DenseComponent c = s.component();
        in.readInt(); // version: fields are matched by name, so no migration is needed yet
        int[] floatSlots = slots(readNames(in), c.floatNames());
        int[] longSlots = slots(readNames(in), c.longNames());
        int[] intSlots = slots(readNames(in), c.intNames());
        int count = in.readInt();
        for (int n = 0; n < count; n++) {
            int i = in.readInt();
            s.add(i);
            for (int slot : floatSlots) {
                float v = in.readFloat();
                if (slot >= 0) s.setFloat(slot, i, v);
            }
            for (int slot : longSlots) {
                long v = in.readLong();
                if (slot >= 0) s.setLong(slot, i, v);
            }
            for (int slot : intSlots) {
                int v = in.readInt();
                if (slot >= 0) s.setInt(slot, i, v);
            }
        }
    }

    private static <T> byte[] writeSparse(SparseStore<T> s) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(s.component().version());
        var entries = s.sorted();
        out.writeInt(entries.size());
        for (var e : entries) {
            out.writeInt(e.getKey());
            writeString(out, SimWorld.encodeJson(s.component(), e.getValue()));
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static <T> void readSparse(SparseStore<T> s, DataInputStream in) throws IOException {
        SparseComponent<T> c = s.component();
        in.readInt(); // version
        int count = in.readInt();
        for (int n = 0; n < count; n++) {
            int i = in.readInt();
            String json = readString(in);
            DataResult<T> result = c.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
            T value = result.result().orElseThrow(() -> new IllegalArgumentException(
                    "Bad saved " + c.id() + ": " + result.error().map(e -> e.message()).orElse("?")));
            s.set(i, value);
        }
    }

    /** For each saved field name, the slot of the registered field with that name, or -1 if it no longer exists. */
    private static int[] slots(List<String> saved, List<String> current) {
        int[] out = new int[saved.size()];
        for (int i = 0; i < out.length; i++) out[i] = current.indexOf(saved.get(i));
        return out;
    }

    private static void writeNames(DataOutputStream out, List<String> names) throws IOException {
        out.writeInt(names.size());
        for (String n : names) writeString(out, n);
    }

    private static List<String> readNames(DataInputStream in) throws IOException {
        int n = in.readInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(readString(in));
        return out;
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] b = new byte[in.readInt()];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
