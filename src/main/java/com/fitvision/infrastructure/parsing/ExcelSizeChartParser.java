package com.fitvision.infrastructure.parsing;

import com.fitvision.domain.sizechart.ParseResult;
import com.fitvision.domain.sizechart.SizeChartFileParser;
import com.fitvision.domain.sizechart.SizeEntryData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses size chart Excel (.xlsx) files into a {@link ParseResult}.
 *
 * <p>Expected column order (row 0 is always a header and is skipped):
 * <pre>size_label, chest_min, chest_max, waist_min, waist_max, hip_min, hip_max, height_min, height_max</pre>
 *
 * <p>Only the first sheet of the workbook is processed. .xls files are not supported.
 * <p>Row limit: 500 data rows (excluding the header). Files exceeding this limit are rejected.
 */
@Component
public class ExcelSizeChartParser implements SizeChartFileParser {

    private static final Logger log = LoggerFactory.getLogger(ExcelSizeChartParser.class);

    private static final int MAX_ROWS = 500;

    // Column indices
    private static final int COL_SIZE_LABEL = 0;
    private static final int COL_CHEST_MIN  = 1;
    private static final int COL_CHEST_MAX  = 2;
    private static final int COL_WAIST_MIN  = 3;
    private static final int COL_WAIST_MAX  = 4;
    private static final int COL_HIP_MIN    = 5;
    private static final int COL_HIP_MAX    = 6;
    private static final int COL_HEIGHT_MIN = 7;
    private static final int COL_HEIGHT_MAX = 8;

    @Override
    public ParseResult parse(InputStream inputStream) {
        List<SizeEntryData> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skippedRows = 0;

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getLastRowNum(); // 0-based last row index

            // Row 0 is the header — data starts at row 1
            int dataRowCount = totalRows; // totalRows == lastRowNum (0-based), data rows = lastRowNum

            if (dataRowCount > MAX_ROWS) {
                return ParseResult.failure(
                        "File exceeds the maximum of " + MAX_ROWS + " data rows. Please split the file.");
            }

            for (int rowIdx = 1; rowIdx <= totalRows; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    skippedRows++;
                    continue;
                }

                int displayRowNum = rowIdx + 1; // 1-based for human-readable warnings

                String sizeLabel = getCellStringValue(row, COL_SIZE_LABEL).trim().toUpperCase();
                if (sizeLabel.isEmpty()) {
                    skippedRows++;
                    log.warn("Excel row {}: skipped — size_label is blank", displayRowNum);
                    continue;
                }

                try {
                    Double chestMin  = parseMeasurement(row, COL_CHEST_MIN,  displayRowNum, "chest_min",  warnings);
                    Double chestMax  = parseMeasurement(row, COL_CHEST_MAX,  displayRowNum, "chest_max",  warnings);
                    Double waistMin  = parseMeasurement(row, COL_WAIST_MIN,  displayRowNum, "waist_min",  warnings);
                    Double waistMax  = parseMeasurement(row, COL_WAIST_MAX,  displayRowNum, "waist_max",  warnings);
                    Double hipMin    = parseMeasurement(row, COL_HIP_MIN,    displayRowNum, "hip_min",    warnings);
                    Double hipMax    = parseMeasurement(row, COL_HIP_MAX,    displayRowNum, "hip_max",    warnings);
                    Double heightMin = parseMeasurement(row, COL_HEIGHT_MIN, displayRowNum, "height_min", warnings);
                    Double heightMax = parseMeasurement(row, COL_HEIGHT_MAX, displayRowNum, "height_max", warnings);

                    entries.add(new SizeEntryData(
                            sizeLabel, chestMin, chestMax, waistMin, waistMax,
                            hipMin, hipMax, heightMin, heightMax));
                } catch (RowSkipSignal e) {
                    skippedRows++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            return ParseResult.failure("Failed to read Excel file: " + e.getMessage());
        }

        return ParseResult.success(entries, warnings, skippedRows);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Reads a cell's value as a trimmed String regardless of cell type.
     */
    private String getCellStringValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                // Return as integer string if it has no fractional part (e.g. "M" stored as number is unusual
                // but size labels like "42" should not appear as "42.0")
                yield (v == Math.floor(v) && !Double.isInfinite(v))
                        ? String.valueOf((long) v)
                        : String.valueOf(v);
            }
            case BLANK, _NONE -> "";
            default -> "";
        };
    }

    /**
     * Parses a measurement value from an Excel cell.
     *
     * @return the parsed value, or {@code null} if the cell is blank
     * @throws RowSkipSignal if the cell contains a non-numeric, non-blank value
     */
    private Double parseMeasurement(Row row, int colIndex, int rowNum, String colName, List<String> warnings) {
        Cell cell = row.getCell(colIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK || cell.getCellType() == CellType._NONE) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String raw = cell.getStringCellValue().trim();
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                String msg = String.format("Row %d: invalid value '%s' in column %s — row skipped", rowNum, raw, colName);
                warnings.add(msg);
                log.warn(msg);
                throw new RowSkipSignal(msg);
            }
        }
        String msg = String.format("Row %d: unexpected cell type in column %s — row skipped", rowNum, colName);
        warnings.add(msg);
        log.warn(msg);
        throw new RowSkipSignal(msg);
    }

    // -----------------------------------------------------------------------
    // Internal signal exception (never escapes this class)
    // -----------------------------------------------------------------------

    private static final class RowSkipSignal extends RuntimeException {
        RowSkipSignal(String message) {
            super(message);
        }
    }
}
