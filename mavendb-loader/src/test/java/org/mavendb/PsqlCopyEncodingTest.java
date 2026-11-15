package org.mavendb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the COPY text encoding (PostgreSQL COPY, MySQL LOAD DATA) in {@link DatabaseRepository}.
 */
@DisplayName("COPY / LOAD DATA encoding Tests")
class PsqlCopyEncodingTest {

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        DatabaseRepository.SqlRow.appendCopyEscaped(sb, value);
        return sb.toString();
    }

    @Test
    @DisplayName("Plain text is copied unchanged")
    void testPlainText() {
        assertEquals("org.apache.maven", escape("org.apache.maven"));
        assertEquals("", escape(""));
    }

    @Test
    @DisplayName("Backslash, newline, carriage return and tab are escaped")
    void testSpecialCharacters() {
        assertEquals("a\\\\b", escape("a\\b"));
        assertEquals("line1\\nline2", escape("line1\nline2"));
        assertEquals("a\\rb", escape("a\rb"));
        assertEquals("col1\\tcol2", escape("col1\tcol2"));
        assertEquals("\\\\\\n\\t", escape("\\\n\t"));
    }

    @Test
    @DisplayName("JSON escapes keep their backslash after COPY decoding")
    void testJsonBackslash() {
        assertEquals("{\"d\": \"say \\\\\"hi\\\\\"\"}", escape("{\"d\": \"say \\\"hi\\\"\"}"));
    }

    @Test
    @DisplayName("Non-ASCII text is encoded as UTF-8")
    void testUnicode() {
        DatabaseRepository.SqlRow row = new DatabaseRepository.SqlRow();
        row.setString(11, "com.例子");
        String line = new String(DatabaseRepository.toCopyData(List.of(row), DatabaseType.PSQL), StandardCharsets.UTF_8);
        assertEquals("com.例子", line.split("\t", -1)[10]);
    }

    @Test
    @DisplayName("A row has 30 tab separated columns: null, numbers, booleans, strings")
    void testRow() {
        DatabaseRepository.SqlRow row = new DatabaseRepository.SqlRow();
        row.setLong(1, 42L);
        row.setInt(2, 3);
        row.setLong(3, 300_000L);
        row.setObject(4, null);
        row.setBoolean(7, true);
        row.setBoolean(8, false);
        row.setString(11, "g\tx");
        row.setString(30, "{\"k\":\"v\"}");

        String data = new String(DatabaseRepository.toCopyData(List.of(row, row), DatabaseType.PSQL), StandardCharsets.UTF_8);
        String[] lines = data.split("\n", -1);
        assertEquals(3, lines.length, "two rows, each ending with a newline");
        assertEquals("", lines[2]);

        String[] cols = lines[0].split("\t", -1);
        assertEquals(30, cols.length);
        assertEquals("42", cols[0]);
        assertEquals("3", cols[1]);
        assertEquals("300000", cols[2]);
        assertEquals("\\N", cols[3]);
        assertEquals("\\N", cols[4]);
        assertEquals("t", cols[6]);
        assertEquals("f", cols[7]);
        assertEquals("g\\tx", cols[10]);
        assertEquals("{\"k\":\"v\"}", cols[29]);
    }

    @Test
    @DisplayName("MySQL LOAD DATA gets booleans as 1/0, everything else as for PostgreSQL")
    void testMysqlRow() {
        DatabaseRepository.SqlRow row = new DatabaseRepository.SqlRow();
        row.setLong(1, 42L);
        row.setBoolean(7, true);
        row.setBoolean(8, false);
        row.setString(11, "g\\x");

        String[] cols = new String(DatabaseRepository.toCopyData(List.of(row), DatabaseType.MYSQL), StandardCharsets.UTF_8)
            .split("\n", -1)[0].split("\t", -1);
        assertEquals(30, cols.length);
        assertEquals("42", cols[0]);
        assertEquals("1", cols[6]);
        assertEquals("0", cols[7]);
        assertEquals("\\N", cols[8]);
        assertEquals("g\\\\x", cols[10]);
    }
}
