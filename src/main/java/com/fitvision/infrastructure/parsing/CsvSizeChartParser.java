package com.fitvision.infrastructure.parsing;

import com.fitvision.domain.sizechart.ParseResult;
import com.fitvision.domain.sizechart.SizeChartFileParser;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses size chart CSV files into a {@link ParseResult}.
 *
 * <p>Expected column order (first row is always a header and is skipped):
 * <pre>size_label, chest_min, chest_max, waist_min, waist_max, hip_min, hip_max, height_min, height_max</pre>
 *
 * <p>Encoding: UTF-8 and UTF-8-BOM are both handled transparently.
 * <p>Row limit: 500 data rows. Files exceeding this limit are rejected with a failure result.
 */
@Component
public class CsvSizeChartParser implements SizeChartFileParser {

    private static final Logger log = LoggerFactory.getLogger(CsvSizeChartParser.class);

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

    private static final int REQUIRED_COLUMNS = 9;

    @Override
    public ParseResult parse(InputStream inputStream) {
        List<SizeEntryData> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skippedRows = 0;
        int dataRowNumber = 0;

        try {
            Reader reader = new BomAwareReader(inputStream);
            RFC4180Parser csvParser = new RFC4180ParserBuilder().build();
            try (CSVReader csvReader = new CSVReaderBuilder(reader).withCSVParser(csvParser).build()) {
                String[] header = csvReader.readNext();
                if (header == null) {
                    return ParseResult.success(entries, warnings, skippedRows);
                }
                String[] row;
                while ((row = csvReader.readNext()) != null) {
                    dataRowNumber++;
                    if (dataRowNumber > MAX_ROWS) {
                        return ParseResult.failure(
                                "File exceeds the maximum of " + MAX_ROWS + " data rows. Please split the file.");
                    }
                    if (row.length < REQUIRED_COLUMNS) {
                        row = padRow(row, REQUIRED_COLUMNS);
                    }
                    String sizeLabel = row[COL_SIZE_LABEL].trim().toUpperCase();
                    if (sizeLabel.isEmpty()) {
                        skippedRows++;
                        log.warn("CSV row {}: skipped — size_label is blank", dataRowNumber);
                        continue;
                    }
                    try {
                        Double chestMin  = parseMeasurement(row, COL_CHEST_MIN,  dataRowNumber, "chest_min",  warnings);
                        Double chestMax  = parseMeasurement(row, COL_CHEST_MAX,  dataRowNumber, "chest_max",  warnings);
                        Double waistMin  = parseMeasurement(row, COL_WAIST_MIN,  dataRowNumber, "waist_min",  warnings);
                        Double waistMax  = parseMeasurement(row, COL_WAIST_MAX,  dataRowNumber, "waist_max",  warnings);
                        Double hipMin    = parseMeasurement(row, COL_HIP_MIN,    dataRowNumber, "hip_min",    warnings);
                        Double hipMax    = parseMeasurement(row, COL_HIP_MAX,    dataRowNumber, "hip_max",    warnings);
                        Double heightMin = parseMeasurement(row, COL_HEIGHT_MIN, dataRowNumber, "height_min", warnings);
                        Double heightMax = parseMeasurement(row, COL_HEIGHT_MAX, dataRowNumber, "height_max", warnings);
                        entries.add(new SizeEntryData(
                                sizeLabel, chestMin, chestMax, waistMin, waistMax,
                                hipMin, hipMax, heightMin, heightMax));
                    } catch (RowSkipSignal e) {
                        skippedRows++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse CSV file", e);
            return ParseResult.failure("Failed to read CSV: " + e.getMessage());
        }
        return ParseResult.success(entries, warnings, skippedRows);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Parses a measurement value from a CSV cell.
     *
     * @return the parsed value, or {@code null} if the cell is blank
     * @throws RowSkipSignal if the cell contains a non-numeric, non-blank value
     */
    private Double parseMeasurement(String[] row, int colIndex, int rowNum, String colName, List<String> warnings) {
        String raw = row[colIndex].trim();
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

    private String[] padRow(String[] row, int length) {
        String[] padded = new String[length];
        System.arraycopy(row, 0, padded, 0, row.length);
        for (int i = row.length; i < length; i++) {
            padded[i] = "";
        }
        return padded;
    }

    // -----------------------------------------------------------------------
    // Internal signal exception (never escapes this class)
    // -----------------------------------------------------------------------

    private static final class RowSkipSignal extends RuntimeException {
        RowSkipSignal(String message) {
            super(message);
        }
    }

    // -----------------------------------------------------------------------
    // BOM-aware reader
    // -----------------------------------------------------------------------

    /**
     * Wraps an InputStream to transparently strip a UTF-8 BOM (EF BB BF) if present.
     * Uses {@link BufferedInputStream} to guarantee mark/reset support.
     */
    private static final class BomAwareReader extends Reader {

        private final InputStreamReader delegate;

        BomAwareReader(InputStream in) throws Exception {
            BufferedInputStream buffered = new BufferedInputStream(in);
            buffered.mark(3);
            byte[] bom = new byte[3];
            int read = buffered.read(bom, 0, 3);
            if (read < 3 || !(bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF)) {
                // Not a UTF-8 BOM — reset so those bytes are included in the stream
                buffered.reset();
            }
            // else: BOM consumed — do not reset
            delegate = new InputStreamReader(buffered, StandardCharsets.UTF_8);
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws java.io.IOException {
            return delegate.read(cbuf, off, len);
        }

        @Override
        public void close() throws java.io.IOException {
            delegate.close();
        }
    }
}
