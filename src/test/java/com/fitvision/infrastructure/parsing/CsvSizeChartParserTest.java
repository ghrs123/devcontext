package com.fitvision.infrastructure.parsing;

import com.fitvision.domain.sizechart.ParseResult;
import com.fitvision.domain.sizechart.SizeEntryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CsvSizeChartParser}.
 *
 * <p>No Spring context is loaded — the parser is instantiated directly.
 * Each test builds a CSV String in memory and feeds it via a ByteArrayInputStream.
 */
class CsvSizeChartParserTest {

    private CsvSizeChartParser parser;

    // Standard header for all test CSVs
    private static final String HEADER =
            "size_label,chest_min,chest_max,waist_min,waist_max,hip_min,hip_max,height_min,height_max\n";

    @BeforeEach
    void setUp() {
        parser = new CsvSizeChartParser();
    }

    // -----------------------------------------------------------------------
    // 1. Valid CSV — all columns populated
    // -----------------------------------------------------------------------

    @Test
    void validCsv_allColumns_returnsSuccessWithCorrectEntryCount() throws Exception {
        String csv = HEADER
                + "S,85.0,90.0,65.0,70.0,88.0,93.0,160.0,170.0\n"
                + "M,90.0,96.0,70.0,76.0,93.0,99.0,170.0,180.0\n"
                + "L,96.0,102.0,76.0,82.0,99.0,105.0,180.0,190.0\n";

        ParseResult result = parser.parse(toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).hasSize(3);
        assertThat(result.getSkippedRows()).isZero();
        assertThat(result.getWarnings()).isEmpty();

        SizeEntryData first = result.getEntries().get(0);
        assertThat(first.sizeLabel()).isEqualTo("S");
        assertThat(first.chestMin()).isEqualTo(85.0);
        assertThat(first.chestMax()).isEqualTo(90.0);
    }

    // -----------------------------------------------------------------------
    // 2. Blank size_label rows → skipped, skippedRows count correct
    // -----------------------------------------------------------------------

    @Test
    void csvWithBlankSizeLabel_skipsRow_incrementsSkippedRows() throws Exception {
        String csv = HEADER
                + "S,85.0,90.0,65.0,70.0,88.0,93.0,160.0,170.0\n"
                + ",90.0,96.0,70.0,76.0,93.0,99.0,170.0,180.0\n"  // blank label — skip
                + "L,96.0,102.0,76.0,82.0,99.0,105.0,180.0,190.0\n";

        ParseResult result = parser.parse(toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).hasSize(2);
        assertThat(result.getSkippedRows()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // 3. Non-numeric measurement → row skipped with warning
    // -----------------------------------------------------------------------

    @Test
    void csvWithNonNumericMeasurement_skipsRowAndAddsWarning() throws Exception {
        String csv = HEADER
                + "S,INVALID,90.0,65.0,70.0,88.0,93.0,160.0,170.0\n"
                + "M,90.0,96.0,70.0,76.0,93.0,99.0,170.0,180.0\n";

        ParseResult result = parser.parse(toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).hasSize(1);
        assertThat(result.getSkippedRows()).isEqualTo(1);
        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getWarnings().get(0)).contains("INVALID");
    }

    // -----------------------------------------------------------------------
    // 4. Header row only → success with empty entries
    // -----------------------------------------------------------------------

    @Test
    void csvWithHeaderOnly_returnsSuccessWithEmptyEntries() throws Exception {
        String csv = HEADER;

        ParseResult result = parser.parse(toStream(csv));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).isEmpty();
        assertThat(result.getSkippedRows()).isZero();
    }

    // -----------------------------------------------------------------------
    // 5. More than 500 data rows → ParseResult.failure
    // -----------------------------------------------------------------------

    @Test
    void csvWith501DataRows_returnsFailure() throws Exception {
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 501; i++) {
            sb.append("S,85.0,90.0,65.0,70.0,88.0,93.0,160.0,170.0\n");
        }

        ParseResult result = parser.parse(toStream(sb.toString()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getWarnings().get(0)).containsIgnoringCase("500");
    }

    // -----------------------------------------------------------------------
    // 6. UTF-8 BOM prefix → parsed correctly (BOM stripped)
    // -----------------------------------------------------------------------

    @Test
    void csvWithUtf8Bom_strippedAndParsedCorrectly() throws Exception {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = (HEADER + "M,90.0,96.0,70.0,76.0,93.0,99.0,170.0,180.0\n")
                .getBytes(StandardCharsets.UTF_8);

        byte[] combined = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(csvBytes, 0, combined, bom.length, csvBytes.length);

        ParseResult result = parser.parse(new ByteArrayInputStream(combined));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).hasSize(1);
        assertThat(result.getEntries().get(0).sizeLabel()).isEqualTo("M");
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private InputStream toStream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
