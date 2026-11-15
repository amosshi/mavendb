package org.mavendb.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import org.apache.ibatis.jdbc.ScriptRunner;

/**
 * Builds a small SQLite file the way mavendb-loader does: its <code>create.sql</code>, rows into <code>gav</code>,
 * then its <code>data-refresh.sql</code> (indexes and the <code>ga</code> summary table).
 * The scripts are copied from mavendb-loader at build time, so the tests follow schema changes.
 */
final class TestDatabase {

    private static Path file;

    private TestDatabase() {
    }

    /** The test database file, built once per JVM. */
    static synchronized Path file() {
        if (file == null) {
            try {
                Path dir = Files.createTempDirectory("mavendb-api-test");
                dir.toFile().deleteOnExit();
                file = dir.resolve("mavendb.sqlite");
                file.toFile().deleteOnExit();
                build(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        return file;
    }

    private static void build(Path db) throws IOException, SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            conn.setAutoCommit(false);
            runScript(conn, "/db/sqlite/create.sql");

            // seqid, group, artifact, version, version_seq, classifier, file name, sha1, file_modified
            Object[][] rows = {
                {1, "org.apache.commons", "commons-lang3", "3.9", 3_009_000_000_000_000L, null,
                    "commons-lang3-3.9.jar", "0000000000000000000000000000000000000039", 1_560_000_000_000L},
                {2, "org.apache.commons", "commons-lang3", "3.10", 3_010_000_000_000_000L, null,
                    "commons-lang3-3.10.jar", "0000000000000000000000000000000000000310", 1_583_000_000_000L},
                {3, "org.apache.commons", "commons-lang3", "3.10", 3_010_000_000_000_000L, "sources",
                    "commons-lang3-3.10-sources.jar", "0000000000000000000000000000000000003105",
                    1_583_000_000_000L},
                {4, "org.apache.commons", "commons-text", "1.0", 1_000_000_000_000_000L, null,
                    "commons-text-1.0.jar", "00000000000000000000000000000000000000a1", 1_490_000_000_000L},
                // Same file name in two groups
                {5, "com.example.one", "core", "1.0", 1_000_000_000_000_000L, null,
                    "core-1.0.jar", "00000000000000000000000000000000000000c1", 1_500_000_000_000L},
                {6, "com.example.two", "core", "1.0", 1_000_000_000_000_000L, null,
                    "core-1.0.jar", "00000000000000000000000000000000000000c2", 1_500_000_000_000L},
            };
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO gav (seqid, group_id, artifact_id, artifact_version, version_seq, major_version,
                                 classifier, file_name, sha1, file_modified, packaging, file_extension, file_size)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, 'jar', 'jar', 1000)
                """)) {
                for (Object[] row : rows) {
                    for (int i = 0; i < row.length; i++) {
                        ps.setObject(i + 1, row[i]);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();

            runScript(conn, "/db/sqlite/data-refresh.sql");
            conn.commit();
        }
    }

    private static void runScript(Connection conn, String resource) throws IOException {
        try (Reader r = new InputStreamReader(
            Objects.requireNonNull(TestDatabase.class.getResourceAsStream(resource), resource),
            StandardCharsets.UTF_8)) {
            ScriptRunner runner = new ScriptRunner(conn);
            runner.setStopOnError(true);
            runner.setLogWriter(null);
            runner.runScript(r);
        }
    }
}
