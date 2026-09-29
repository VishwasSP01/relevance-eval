package io.github.vishwassp01.relevanceeval.clicks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickLogReaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Parses valid CSV rows and quotes correctly")
    void parsesValidCsvRows() throws Exception {
        String csv = """
                query,documentId,position,clicked
                "running shoes",SKU-1024,1,true
                running shoes,SKU-2048,2,false
                """;

        List<ClickEvent> events = ClickLogReader.read(new BufferedReader(new StringReader(csv)));
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isEqualTo(new ClickEvent("running shoes", "SKU-1024", 1, true));
        assertThat(events.get(1)).isEqualTo(new ClickEvent("running shoes", "SKU-2048", 2, false));
    }

    @Test
    @DisplayName("Rejects empty CSV file naming line 1")
    void rejectsEmptyCsv() {
        assertThatThrownBy(() -> ClickLogReader.read(new BufferedReader(new StringReader(""))))
                .isInstanceOf(ClickLogParseException.class)
                .hasMessageContaining("Line 1")
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Rejects missing or invalid header naming line 1")
    void rejectsInvalidHeader() {
        String csv = """
                query,sku,rank,is_clicked
                running shoes,SKU-1024,1,true
                """;
        assertThatThrownBy(() -> ClickLogReader.read(new BufferedReader(new StringReader(csv))))
                .isInstanceOf(ClickLogParseException.class)
                .hasMessageContaining("Line 1")
                .hasMessageContaining("header");
    }

    @Test
    @DisplayName("Rejects non-positive position naming line number")
    void rejectsNonPositivePosition() {
        String csv = """
                query,documentId,position,clicked
                running shoes,SKU-1024,1,true
                running shoes,SKU-2048,0,false
                """;
        assertThatThrownBy(() -> ClickLogReader.read(new BufferedReader(new StringReader(csv))))
                .isInstanceOf(ClickLogParseException.class)
                .hasMessageContaining("Line 3")
                .hasMessageContaining("position");

        String csvNegative = """
                query,documentId,position,clicked
                running shoes,SKU-1024,-5,true
                """;
        assertThatThrownBy(() -> ClickLogReader.read(new BufferedReader(new StringReader(csvNegative))))
                .isInstanceOf(ClickLogParseException.class)
                .hasMessageContaining("Line 2")
                .hasMessageContaining("position");
    }

    @Test
    @DisplayName("Rejects invalid clicked boolean value naming line number")
    void rejectsInvalidClickedValue() {
        String csv = """
                query,documentId,position,clicked
                running shoes,SKU-1024,1,yes
                """;
        assertThatThrownBy(() -> ClickLogReader.read(new BufferedReader(new StringReader(csv))))
                .isInstanceOf(ClickLogParseException.class)
                .hasMessageContaining("Line 2")
                .hasMessageContaining("clicked");
    }

    @Test
    @DisplayName("Rejects wrong column count naming line number")
    void rejectsWrongColumnCount() {
        String csv = """
                query,documentId,position,clicked
                running shoes,SKU-1024,1
                """;
        assertThatThrownBy(() -> ClickLogReader.read(new BufferedReader(new StringReader(csv))))
                .isInstanceOf(ClickLogParseException.class)
                .hasMessageContaining("Line 2")
                .hasMessageContaining("Expected 4 columns");
    }

    @Test
    @DisplayName("Rejects unclosed quotes naming line number")
    void rejectsUnclosedQuotes() {
        String csv = """
                query,documentId,position,clicked
                "unclosed query,SKU-1,1,true
                """;
        assertThatThrownBy(() -> ClickLogReader.read(new BufferedReader(new StringReader(csv))))
                .isInstanceOf(ClickLogParseException.class)
                .hasMessageContaining("Line 2")
                .hasMessageContaining("Unclosed quotation mark");
    }
}
