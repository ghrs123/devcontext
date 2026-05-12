package com.fitvision.infrastructure.parsing;

import com.fitvision.domain.sizechart.ParseResult;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.fitvision.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExcelSizeChartParser}.
 *
 * <p>No Spring context is loaded. Excel workbooks are built in-memory using Apache POI
 * and fed to the parser via a ByteArrayInputStream.
 */
class ExcelSizeChartParserTest {

    private ExcelSizeChartParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExcelSizeChartParser();
    }

    // -----------------------------------------------------------------------
    // 1. Valid .xlsx — all columns populated → success
    // -----------------------------------------------------------------------

    @Test
    void validExcel_allColumns_returnsSuccessWithCorrectEntryCount() throws Exception {
        InputStream input = new ByteArrayInputStream(TestDataBuilder.buildValidExcel(3));

        ParseResult result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).hasSize(3);
        assertThat(result.getSkippedRows()).isZero();

        SizeEntryData first = result.getEntries().get(0);
        assertThat(first.sizeLabel()).isEqualTo("S");
        assertThat(first.chestMin()).isEqualTo(115.0);
    }

    // -----------------------------------------------------------------------
    // 2. .xlsx with blank size_label rows → skipped correctly
    // -----------------------------------------------------------------------

    @Test
    void excelWithBlankSizeLabel_skipsRow_incrementsSkippedRows() throws Exception {
        InputStream input = new ByteArrayInputStream(TestDataBuilder.buildExcelWithBlankRows());

        ParseResult result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).hasSize(2);
        assertThat(result.getSkippedRows()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // 3. Header row only → success with empty entries
    // -----------------------------------------------------------------------

    @Test
    void excelWithHeaderOnly_returnsSuccessWithEmptyEntries() throws Exception {
        InputStream input = new ByteArrayInputStream(TestDataBuilder.buildHeaderOnlyExcel());

        ParseResult result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).isEmpty();
        assertThat(result.getSkippedRows()).isZero();
    }

    // -----------------------------------------------------------------------
    // 4. More than 500 data rows → ParseResult.failure
    // -----------------------------------------------------------------------

    @Test
    void excelWith501DataRows_returnsFailure() throws Exception {
        InputStream input = new ByteArrayInputStream(TestDataBuilder.buildOversizedExcel());

        ParseResult result = parser.parse(input);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getWarnings().get(0)).containsIgnoringCase("500");
    }

}
