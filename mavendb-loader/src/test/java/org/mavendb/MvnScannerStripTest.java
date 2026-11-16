package org.mavendb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MvnScanner#strip(String)}.
 */
@DisplayName("MvnScanner strip Tests")
class MvnScannerStripTest {

    @Test
    @DisplayName("null, empty and blank give null")
    void testNullAndBlank() {
        assertNull(MvnScanner.strip(null));
        assertNull(MvnScanner.strip(""));
        assertNull(MvnScanner.strip(" \t\n"));
    }

    @Test
    @DisplayName("Whitespace, then double quotes, are stripped from both ends")
    void testStrip() {
        assertEquals("junit", MvnScanner.strip("junit"));
        assertEquals("junit", MvnScanner.strip("  junit\t"));
        assertEquals("junit", MvnScanner.strip(" \"junit\" "));
        assertEquals("a \"b\" c", MvnScanner.strip("\"\"a \"b\" c\""));
        // Whitespace inside the quotes stays
        assertEquals(" junit ", MvnScanner.strip("\" junit \""));
    }

    @Test
    @DisplayName("Only quotes give an empty string, not null")
    void testOnlyQuotes() {
        assertEquals("", MvnScanner.strip("\"\""));
    }
}
