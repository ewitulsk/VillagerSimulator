package com.ewitulsk.villagersimulator.core.data;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Loads data definitions from the classpath, for the headless harness. Scans every classpath root that contains a
 * {@code data/} directory (both directories and jars).
 */
public final class ClasspathDataSource implements DataSource {
    private final ClassLoader loader;

    public ClasspathDataSource(ClassLoader loader) {
        this.loader = loader;
    }

    public static ClasspathDataSource of(Class<?> anchor) {
        return new ClasspathDataSource(anchor.getClassLoader());
    }

    @Override
    public Map<Id, JsonElement> load(String folder) {
        Map<Id, JsonElement> out = new TreeMap<>();
        try {
            Enumeration<URL> roots = loader.getResources("data");
            for (URL url : Collections.list(roots)) scan(toPath(url.toURI()), folder, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static Path toPath(URI uri) throws IOException {
        if ("jar".equals(uri.getScheme())) {
            FileSystem fs;
            try {
                fs = FileSystems.newFileSystem(uri, Map.of());
            } catch (FileSystemAlreadyExistsException e) {
                fs = FileSystems.getFileSystem(uri);
            }
            return fs.provider().getPath(uri);
        }
        return Path.of(uri);
    }

    private static void scan(Path dataDir, String folder, Map<Id, JsonElement> out) throws IOException {
        if (!Files.isDirectory(dataDir)) return;
        try (Stream<Path> namespaces = Files.list(dataDir)) {
            for (Path ns : namespaces.toList()) {
                Path dir = ns.resolve("villagersimulator").resolve(folder);
                if (!Files.isDirectory(dir)) continue;
                String namespace = ns.getFileName().toString().replace("/", "");
                try (Stream<Path> files = Files.walk(dir)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                        String rel = dir.relativize(file).toString().replace('\\', '/');
                        Id id = Id.of(namespace, rel.substring(0, rel.length() - ".json".length()));
                        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            out.put(id, JsonParser.parseReader(r));
                        }
                    }
                }
            }
        }
    }
}
