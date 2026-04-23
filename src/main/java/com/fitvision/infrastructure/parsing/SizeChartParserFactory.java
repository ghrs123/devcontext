package com.fitvision.infrastructure.parsing;

import com.fitvision.domain.sizechart.SizeChartFileParser;
import com.fitvision.shared.exception.UnsupportedFileFormatException;
import org.springframework.stereotype.Component;

/**
 * Factory that selects the correct {@link SizeChartFileParser} based on the file's
 * content type and filename extension.
 *
 * <p>Detection order:
 * <ol>
 *   <li>Content-Type header is checked first.</li>
 *   <li>Filename extension is used as a fallback when the content type is generic
 *       (e.g. {@code application/octet-stream}).</li>
 * </ol>
 */
@Component
public class SizeChartParserFactory {

    private final CsvSizeChartParser csvParser;
    private final ExcelSizeChartParser excelParser;

    public SizeChartParserFactory(CsvSizeChartParser csvParser, ExcelSizeChartParser excelParser) {
        this.csvParser = csvParser;
        this.excelParser = excelParser;
    }

    /**
     * Returns the appropriate parser for the given file.
     *
     * @param contentType the HTTP Content-Type of the uploaded file (may be null)
     * @param filename    the original filename including extension (may be null)
     * @return the matching parser — never null
     * @throws UnsupportedFileFormatException if neither content type nor extension is recognised
     */
    public SizeChartFileParser getParser(String contentType, String filename) {
        // Normalise
        String ct = contentType != null ? contentType.toLowerCase().trim() : "";
        String name = filename != null ? filename.toLowerCase().trim() : "";

        // --- CSV detection ---
        if (ct.contains("text/csv")
                || ct.contains("application/csv")
                || ct.contains("text/comma-separated-values")) {
            return csvParser;
        }
        if (name.endsWith(".csv")) {
            return csvParser;
        }

        // --- Excel detection ---
        if (ct.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || ct.contains("application/vnd.ms-excel.sheet")) {
            return excelParser;
        }
        if (name.endsWith(".xlsx")) {
            return excelParser;
        }

        // --- Unsupported .xls (legacy Excel) — give a specific message ---
        if (name.endsWith(".xls") || ct.contains("application/vnd.ms-excel")) {
            throw new UnsupportedFileFormatException(
                    "Legacy Excel format (.xls) is not supported. Please save your file as .xlsx and re-upload.");
        }

        // --- Generic octet-stream — try extension fallback above already handled ---
        throw new UnsupportedFileFormatException(
                "Unsupported file format (content-type: " + contentType + ", filename: " + filename
                        + "). Accepted formats: CSV (.csv), Excel (.xlsx).");
    }
}
