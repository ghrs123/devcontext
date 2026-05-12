package com.fitvision.testutil;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;

/**
 * Utility helpers for building in-memory test payloads.
 */
public final class TestDataBuilder {

    private static final String[] HEADER_COLS = {
            "size_label", "chest_min", "chest_max",
            "waist_min", "waist_max",
            "hip_min", "hip_max",
            "height_min", "height_max"
    };

    private TestDataBuilder() {
    }

    public static byte[] buildValidExcel(int rows) {
        return buildWorkbook(rows, false);
    }

    public static byte[] buildExcelWithBlankRows() {
        return buildWorkbook(3, true);
    }

    public static byte[] buildHeaderOnlyExcel() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            writeHeader(sheet, 0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build header-only Excel test payload", e);
        }
    }

    public static byte[] buildOversizedExcel() {
        // 501 data rows exceeds the parser limit (500).
        return buildWorkbook(501, false);
    }

    public static byte[] buildLargeFileOver2Mb() {
        // Slightly above 2MB to trigger multipart max-file-size enforcement.
        return new byte[2_100_000];
    }

    private static byte[] buildWorkbook(int dataRows, boolean includeBlankRow) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            writeHeader(sheet, 0);
            for (int i = 1; i <= dataRows; i++) {
                String sizeLabel = includeBlankRow && i == 2 ? "" : (i % 3 == 0 ? "L" : (i % 2 == 0 ? "M" : "S"));
                writeDataRow(sheet, i, sizeLabel,
                        115.0, 125.0,
                        55.0, 65.0,
                        122.0, 133.0,
                        160.0, 190.0);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Excel test payload", e);
        }
    }

    private static void writeHeader(Sheet sheet, int rowIdx) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < HEADER_COLS.length; i++) {
            row.createCell(i).setCellValue(HEADER_COLS[i]);
        }
    }

    private static void writeDataRow(Sheet sheet, int rowIdx, String sizeLabel,
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
}
