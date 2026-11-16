package org.mavendb.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the OpenSearch {@code _bulk} encoding in {@link OpenSearchClient}.
 */
@DisplayName("OpenSearch bulk encoding Tests")
class OpenSearchBulkTest {

    @Test
    @DisplayName("Each document is an index action with its _id, then the source without _id")
    void testBulkData() {
        Document first = new Document("_id", 42L).append("groupId", "org.example").append("fileSize", 3_000_000_000L)
            .append("hasSources", true).append("name", "tab\there \"quoted\" 例子 😀");
        Document second = new Document("_id", 43L).append("groupId", "g").append("classNames", List.of("a.B", "c.D"));

        String body = new String(OpenSearchClient.toBulkData(List.of(first, second)), StandardCharsets.UTF_8);
        String[] lines = body.split("\n", -1);
        assertEquals(5, lines.length, "two documents, two lines each, newline terminated");
        assertEquals("", lines[4]);

        assertEquals("{\"index\":{\"_id\":\"42\"}}", lines[0]);
        Document source = Document.parse(lines[1]);
        assertFalse(source.containsKey("_id"), "_id is metadata, not allowed in the source");
        assertEquals("org.example", source.getString("groupId"));
        assertEquals(3_000_000_000L, source.get("fileSize", Number.class).longValue());
        assertEquals("tab\there \"quoted\" 例子 😀", source.getString("name"));
        assertFalse(lines[1].contains("$numberLong"), "relaxed JSON: plain numbers");

        assertEquals("{\"index\":{\"_id\":\"43\"}}", lines[2]);
        assertEquals(List.of("a.B", "c.D"), Document.parse(lines[3]).getList("classNames", String.class));
    }

    @Test
    @DisplayName("Bulk bodies split at document boundaries, under the size limit; an oversized document goes alone")
    void testSplitBulk() {
        List<Document> documents = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            // Document 7 is larger than the limit; the others vary, with escaped newlines in the text
            int length = i == 7 ? 5_000 : 100 + i * 37;
            documents.add(new Document("_id", (long) i).append("text", "x\n".repeat(length / 2)));
        }
        byte[] data = OpenSearchClient.toBulkData(documents);
        int maxBytes = 2_000;

        List<int[]> ranges = OpenSearchClient.splitBulk(data, maxBytes);
        int expectedStart = 0;
        int docs = 0;
        for (int[] range : ranges) {
            assertEquals(expectedStart, range[0], "ranges are contiguous");
            String part = new String(data, range[0], range[1] - range[0], StandardCharsets.UTF_8);
            String[] lines = part.split("\n");
            assertEquals(0, lines.length % 2, "whole documents only");
            assertTrue(lines[0].startsWith("{\"index\""));
            assertTrue(part.endsWith("\n"));
            assertTrue(range[1] - range[0] <= maxBytes || lines.length == 2, "over the limit only for a single document");
            docs += lines.length / 2;
            expectedStart = range[1];
        }
        assertEquals(data.length, expectedStart, "ranges cover the whole body");
        assertEquals(20, docs);
        assertTrue(ranges.size() > 3);

        assertEquals(1, OpenSearchClient.splitBulk(data, Integer.MAX_VALUE).size(), "a small body is one request");
        assertTrue(OpenSearchClient.splitBulk(new byte[0], maxBytes).isEmpty());
    }

    @Test
    @DisplayName("OpenSearch index names are lower case")
    void testIndexName() {
        assertEquals("central", DatabaseRepository.openSearchIndexName("Central"));
    }
}
