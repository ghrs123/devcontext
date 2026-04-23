package com.fitvision.infrastructure.parsing;

import com.fitvision.domain.sizechart.ParseResult;
import com.fitvision.domain.sizechart.SizeEntryData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
        InputStream input = buildExcel(wb -> {
            Sheet sheet = wb.createSheet();
            writeHeader(sheet, 0);
            writeDataRow(sheet, 1, "S", 85.0, 90.0, 65.0, 70.0, 88.0, 93.0, 160.0, 170.0);
            writeDataRow(sheet, 2, "M", 90.0, 96.0, 70.0, 76.0, 93.0, 99.0, 170.0, 180.0);
            writeDataRow(sheet, 3, "L", 96.0, 102.0, 76.0, 82.0, 99.0, 105.0, 180.0, 190.0);
        });

        ParseResult result = parser.parse(input);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntries()).hasSize(3);
        assertThat(result.getSkippedRows()).isZero();

        SizeEntryData first = result.getEntries().get(0);
        assertThat(first.sizeLabel()).isEqualTo("S");
        assertThat(first.chestMin()).isEqualTo(85.0);
    }

    // -----------------------------------------------------------------------
    // 2. .xlsx with blank size_label rows → skipped correctly
    // -----------------------------------------------------------------------

    @Test
    void excelWithBlankSizeLabel_skipsRow_incrementsSkippedRows() throws Exception {
        InputStream input = buildExcel(wb -> {
            Sheet sheet = wb.createSheet();
            writeHeader(sheet, 0);
            writeDataRow(sheet, 1, "S", 85.0, 90.0, 65.0, 70.0, 88.0, 93.0, 160.0, 170.0);
            writeDataRow(sheet, 2, "",  90.0, 96.0, 70.0, 76.0, 93.0, 99.0, 170.0, 180.0); // blank
            writeDataRow(sheet, 3, "L", 96.0, 102.0, 76.0, 82.0, 99.0, 105.0, 180.0, 190.0);
        });

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
        InputStream input = buildExcel(wb -> {
            Sheet sheet = wb.createSheet();
            writeHeader(sheet, 0);
        });

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
        InputStream input = buildExcel(wb -> {
            Sheet sheet = wb.createSheet();
            writeHeader(sheet, 0);
            for (int i = 1; i <= 501; i++) {
                writeDataRow(sheet, i, "S", 85.0, 90.0, 65.0, 70.0, 88.0, 93.0, 160.0, 170.0);
            }
        });

        ParseResult result = parser.parse(input);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getWarnings().get(0)).containsIgnoringCase("500");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static final String[] HEADER_COLS = {
            "size_label", "chest_min", "chest_max",
            "waist_min", "waist_max",
            "hip_min", "hip_max",
            "height_min", "height_max"
    };

    private void writeHeader(Sheet sheet, int rowIdx) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < HEADER_COLS.length; i++) {
            row.createCell(i).setCellValue(HEADER_COLS[i]);
        }
    }

    private void writeDataRow(Sheet sheet, int rowIdx, String sizeLabel,
                               double chestMin, double chestMax,
                               double waistMin, double waistMax,
                               double hipMin, double hipMax,
                               double heightMin, double heightMax) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(sizeLabel);
        row.createCell(1).setCellValue(chestMin);
        row.createCell(2).setCellValue(chestMax);
        row.createCell(3).setCellValue(waistMin);
        row.createCell(4).setCellValue(waistMax);
        row.createCell(5).setCellValue(hipMin);
        row.createCell(6).setCellValue(hipMax);
        row.createCell(7).setCellValue(heightMin);
        row.createCell(8).setCellValue(heightMax);
    }

    @FunctionalInterface
    interface WorkbookConfigurer {
        void configure(XSSFWorkbook wb) throws Exception;
    }

    private InputStream buildExcel(WorkbookConfigurer configurer) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            configurer.configure(wb);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
