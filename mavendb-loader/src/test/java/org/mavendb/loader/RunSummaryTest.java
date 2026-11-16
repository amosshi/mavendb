package org.mavendb.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link RunSummary}.
 */
@DisplayName("Run summary Tests")
class RunSummaryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Credentials are masked in connection URLs")
    void testMaskUrl() {
        assertEquals("mongodb://***@localhost:27017/admin", RunSummary.maskUrl("mongodb://root:secret@localhost:27017/admin"));
        assertEquals("jdbc:mysql://h:3306/db?user=a&password=***&x=1", RunSummary.maskUrl("jdbc:mysql://h:3306/db?user=a&password=secret&x=1"));
        assertEquals("jdbc:duckdb:mavendb.duckdb", RunSummary.maskUrl("jdbc:duckdb:mavendb.duckdb"));
        assertNull(RunSummary.maskUrl(null));
    }

    @Test
    @DisplayName("A successful run has durations, record counts and throughput")
    void testSuccess() throws Exception {
        RunSummary summary = new RunSummary(DatabaseType.MYSQL, "file:///index/", "20261114.120000-mysql.log");
        summary.setDatabaseUrl("jdbc:mysql://127.0.0.1:3306/mavendb");
        summary.setDatabaseSizeBytes(3L * 1024 * 1024 * 1024 / 2);
        summary.putDuration("createSchema", 65);
        summary.putDuration("import", 2_000);
        summary.putDuration("refresh", 1_000);
        summary.setRecords(1_003, 1_000);
        summary.putTableRows("g", 10);
        summary.putSetting("batchSize", 50_000);
        summary.setMaxFieldLengths(Map.of("version", 145));
        Instant published = Instant.now().minus(36, ChronoUnit.HOURS);
        summary.setIndexTimestamp(published);
        summary.addIndexChunk("nexus-maven-repository-index.gz", 1, published);
        summary.succeeded();

        Path file = this.tempDir.resolve("run.json");
        summary.write(file);
        Document doc = Document.parse(Files.readString(file));

        assertEquals("SUCCESS", doc.getString("status"));
        assertFalse(doc.containsKey("error"));
        assertEquals("mysql", doc.getString("dbType"));
        Document durations = doc.get("durationsMs", Document.class);
        assertTrue(durations.containsKey("total"));
        assertEquals(2_000, durations.get("import", Number.class).longValue());
        Document records = doc.get("records", Document.class);
        assertEquals(1_000, records.get("stored", Number.class).longValue());
        assertEquals(3, records.get("skipped", Number.class).longValue());
        assertEquals(500, records.get("importPerSecond", Number.class).longValue());
        assertEquals(10, doc.get("tableRows", Document.class).get("g", Number.class).longValue());
        assertEquals(145, doc.get("maxFieldLengths", Document.class).get("version", Number.class).intValue());
        assertEquals("20261114.120000-mysql.log", doc.getString("logFile"));
        Document database = doc.get("database", Document.class);
        assertEquals(1_610_612_736L, database.get("sizeBytes", Number.class).longValue());
        assertEquals(1.5, database.get("sizeGiB", Number.class).doubleValue());

        assertEquals(published.toString(), doc.getString("indexTimestamp"));
        assertEquals(1.5, doc.get("indexAgeDays", Number.class).doubleValue());
        List<Document> chunks = doc.get("index", Document.class).getList("chunks", Document.class);
        assertEquals(1, chunks.size());
        assertEquals("nexus-maven-repository-index.gz", chunks.get(0).getString("name"));
        assertEquals(published.toString(), chunks.get(0).getString("timestamp"));
    }

    @Test
    @DisplayName("A failed run records the error")
    void testFailure() {
        RunSummary summary = new RunSummary(DatabaseType.PSQL, "file:///index/", "x.log");
        summary.failed(new IllegalStateException("boom"));
        Document doc = summary.toDocument();
        assertEquals("FAILED", doc.getString("status"));
        assertEquals("java.lang.IllegalStateException: boom", doc.getString("error"));
        assertFalse(doc.get("records", Document.class).containsKey("importPerSecond"));
        assertTrue(doc.containsKey("indexTimestamp"), "always present, null when the index was not read");
        assertNull(doc.get("indexTimestamp"));
        assertFalse(doc.containsKey("indexAgeDays"));
    }
}
