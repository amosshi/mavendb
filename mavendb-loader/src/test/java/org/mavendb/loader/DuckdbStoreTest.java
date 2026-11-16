package org.mavendb.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link DatabaseRepository#storeDuckDB(List, long)} against a DuckDB file created by db/duckdb/create.sql.
 */
@DisplayName("DuckDB Appender Tests")
class DuckdbStoreTest {

    private static final String[] TRICKY = {
        "tab\there", "nl\nhere", "back\\slash", "\\N", "NULL", "emoji 😀 例子", "", "ctrl\u0001\u0000z"
    };

    @TempDir
    Path tempDir;

    private String createDatabase() throws IOException, SQLException {
        String url = "jdbc:duckdb:" + this.tempDir.resolve("test.duckdb");
        String script = Files.readString(Path.of("src/main/resources/db/duckdb/create.sql"), StandardCharsets.UTF_8);
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            for (String sql : script.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!sql.isBlank()) {
                    stmt.execute(sql);
                }
            }
        }
        return url;
    }

    private static DatabaseRepository.SqlRow row(int i) {
        DatabaseRepository.SqlRow row = new DatabaseRepository.SqlRow();
        row.setLong(1, i + 1L);
        row.setInt(2, 7);
        row.setLong(3, 123_456_789_012L);
        row.setObject(4, (long) i);
        row.setObject(5, null);
        row.setBoolean(7, true);
        row.setBoolean(8, false);
        row.setString(10, null);
        row.setString(11, "g" + i);
        row.setString(12, "a");
        row.setString(13, "v");
        for (int c = 14; c <= 29; c++) {
            row.setString(c, TRICKY[i]);
        }
        row.setString(30, "{\"k\":\"say \\\"hi\\\"\",\"i\":" + i + "}");
        return row;
    }

    @Test
    @DisplayName("Rows round trip through the Appender: numbers, booleans, nulls, special characters, JSON")
    void testRoundTrip() throws Exception {
        String url = createDatabase();
        List<DatabaseRepository.SqlRow> rows = new ArrayList<>();
        for (int i = 0; i < TRICKY.length; i++) {
            rows.add(row(i));
        }

        try (DatabaseRepository repo = new DatabaseRepository(DatabaseType.DUCKDB, url, new Properties())) {
            repo.storeDuckDB(rows, rows.size());
            // At least the header and one block of the file
            assertTrue(repo.databaseSizeBytes("central") > 0, "database size");
        }

        try (Connection conn = DriverManager.getConnection(url);
             ResultSet rs = conn.createStatement().executeQuery("SELECT *, json->>'k' AS k FROM gav ORDER BY seqid")) {
            int i = 0;
            while (rs.next()) {
                assertEquals(i + 1L, rs.getLong("seqid"));
                assertEquals(7, rs.getInt("major_version"));
                assertEquals(123_456_789_012L, rs.getLong("version_seq"));
                assertEquals(i, rs.getLong("record_modified"));
                assertNull(rs.getObject("file_modified"));
                assertNull(rs.getObject("file_size"), "a parameter that is never set is null");
                assertTrue(rs.getBoolean("has_signature"));
                assertFalse(rs.getBoolean("has_sources"));
                assertNull(rs.getObject("sha1"));
                assertEquals("g" + i, rs.getString("group_id"));
                for (String col : List.of("classifier", "name", "description", "bundle_docurl", "export_package", "export_service")) {
                    assertEquals(TRICKY[i], rs.getString(col), col + " of row " + i);
                }
                assertEquals("say \"hi\"", rs.getString("k"));
                i++;
            }
            assertEquals(TRICKY.length, i);
        }
    }
}
