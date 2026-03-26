package com.sayu.payroll.util;

import com.sayu.payroll.dto.PayrollSummaryDTO;
import com.sayu.payroll.exception.PayrollProcessingException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for parsing attendance Excel files and exporting results.
 *
 * Expected sheet/columns (fixed mapping):
 * - Sheet: "Exception Stat."
 * - Column B (index 1): Employee Name
 * - Column D (index 3): Date (read/ignored for MVP)
 * - Columns E-H (indices 4-7): time logs
 *   - E-F: Time In / Time Out (shift 1)
 *   - G-H: Time In / Time Out (shift 2)
 *
 * Time format: 24-hour time (e.g. 13:00, 22:00).
 */
@Component
public class ExcelPayrollParser {

    // Returned to PayrollService: each entry represents one shift pair (Time In / Time Out).
    public record EmployeeTimeLog(String name, LocalTime timeIn, LocalTime timeOut) {}

    private static final String TARGET_SHEET_NAME = "Exception Stat.";

    // Fixed column mapping (0-based indices).
    private static final int COL_EMPLOYEE_NAME = 1; // B
    private static final int COL_DATE = 3; // D (read/ignored)
    private static final int COL_E = 4; // E
    private static final int COL_F = 5; // F
    private static final int COL_G = 6; // G
    private static final int COL_H = 7; // H

    public List<EmployeeTimeLog> parseAttendanceRows(InputStream excelInputStream) {
        try (Workbook workbook = WorkbookFactory.create(excelInputStream)) {
            Sheet sheet = workbook.getSheet(TARGET_SHEET_NAME);
            if (sheet == null) {
                throw new PayrollProcessingException("Excel sheet '" + TARGET_SHEET_NAME + "' not found.");
            }

            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            List<EmployeeTimeLog> timeLogs = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                String name = readCellAsString(row.getCell(COL_EMPLOYEE_NAME), formatter);
                if (name.isEmpty()) {
                    continue;
                }

                // Time logs extraction from columns E-H.
                LocalTime tE = parseOptionalLocalTime(row.getCell(COL_E), formatter, evaluator);
                LocalTime tF = parseOptionalLocalTime(row.getCell(COL_F), formatter, evaluator);
                LocalTime tG = parseOptionalLocalTime(row.getCell(COL_G), formatter, evaluator);
                LocalTime tH = parseOptionalLocalTime(row.getCell(COL_H), formatter, evaluator);

                List<LocalTime> validTimes = new ArrayList<>(4);
                if (tE != null) validTimes.add(tE);
                if (tF != null) validTimes.add(tF);
                if (tG != null) validTimes.add(tG);
                if (tH != null) validTimes.add(tH);

                // Skip header-like rows (Name present, but times are not parseable).
                if (validTimes.size() < 2) {
                    if (name.trim().toLowerCase(Locale.ENGLISH).contains("name")) {
                        continue;
                    }
                    continue;
                }

                // Business rule:
                // - first two valid values = Time In / Time Out (single shift)
                // - if more than 2 valid entries exist, process as pairs (E-F) and (G-H)
                if (validTimes.size() == 2) {
                    timeLogs.add(new EmployeeTimeLog(name, validTimes.get(0), validTimes.get(1)));
                } else {
                    if (tE != null && tF != null) {
                        timeLogs.add(new EmployeeTimeLog(name, tE, tF));
                    }
                    if (tG != null && tH != null) {
                        timeLogs.add(new EmployeeTimeLog(name, tG, tH));
                    }
                }
            }

            if (timeLogs.isEmpty()) {
                throw new PayrollProcessingException("Excel file contains no valid time logs in sheet '" + TARGET_SHEET_NAME + "'.");
            }

            return timeLogs;
        } catch (PayrollProcessingException e) {
            throw e;
        } catch (Exception e) {
            // POI can throw various runtime/format exceptions for malformed or unexpected Excel content.
            throw new PayrollProcessingException("Failed to read Excel file.", e);
        }
    }

    private String readCellAsString(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }

    /**
     * Parse a LocalTime from a POI cell (strict).
     *
     * Supports:
     * - numeric Excel time values
     * - strings like "08:30", "8:30 AM", "08:30:00"
     */
    private LocalTime parseLocalTime(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) throw new PayrollProcessingException("Missing required time cell.");

        try {
            CellType type = cell.getCellType();

            if (type == CellType.FORMULA) {
                // Prefer using the formatted value for formula cells.
                String formatted = formatter.formatCellValue(cell, evaluator).trim();
                return parseLocalTimeFromString(formatted);
            }

            if (type == CellType.NUMERIC) {
                // Excel stores times as a fraction of a day. DateUtil converts to a Date.
                java.util.Date date = DateUtil.getJavaDate(cell.getNumericCellValue());
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
            }

            String formatted = formatter.formatCellValue(cell, evaluator).trim();
            return parseLocalTimeFromString(formatted);
        } catch (RuntimeException e) {
            if (e instanceof PayrollProcessingException) throw e;
            throw new PayrollProcessingException("Invalid time format in Excel. " + e.getMessage(), e);
        }
    }

    private LocalTime parseOptionalLocalTime(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        try {
            if (cell == null) return null;
            return parseLocalTime(cell, formatter, evaluator);
        } catch (PayrollProcessingException ignored) {
            return null;
        }
    }

    private LocalTime parseLocalTimeFromString(String raw) {
        if (raw == null) {
            throw new PayrollProcessingException("Time value is empty.");
        }

        String s = raw.trim();
        if (s.isEmpty()) {
            throw new PayrollProcessingException("Time value is empty.");
        }

        // Try to normalize cases like: "08:30 AM" or "08:30:00 PM".
        s = normalizeAmPm(s);

        // Business requirement: 24-hour times. We still accept AM/PM formats from real-world Excel exports.
        List<DateTimeFormatter> formatters = List.of(
                new DateTimeFormatterBuilder().appendPattern("H:mm").toFormatter(),
                new DateTimeFormatterBuilder().appendPattern("HH:mm").toFormatter(),
                new DateTimeFormatterBuilder().appendPattern("H:mm:ss").toFormatter(),
                new DateTimeFormatterBuilder().appendPattern("HH:mm:ss").toFormatter(),
                new DateTimeFormatterBuilder().appendPattern("hh:mm a").toFormatter(),
                new DateTimeFormatterBuilder().appendPattern("hh:mm:ss a").toFormatter()
        );

        for (DateTimeFormatter fmt : formatters) {
            try {
                // LocalTime.parse uses the formatter; use it directly.
                return LocalTime.parse(s.toUpperCase(Locale.ENGLISH), fmt);
            } catch (DateTimeParseException ignored) {
                // keep trying
            }
        }

        // If Excel provided a combined date+time string, attempt to extract the time portion.
        String extracted = extractTimePart(s);
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalTime.parse(extracted.toUpperCase(Locale.ENGLISH), fmt);
            } catch (DateTimeParseException ignored) {
                // keep trying
            }
        }

        throw new PayrollProcessingException("Unrecognized time string: '" + raw + "'.");
    }

    private String normalizeAmPm(String s) {
        // Normalize AM/PM tokens to a consistent spacing.
        String trimmed = s.trim();
        String upper = trimmed.toUpperCase(Locale.ENGLISH);
        if (upper.endsWith("AM") || upper.endsWith("PM")) {
            // Ensure single space before AM/PM.
            return upper.replaceAll("\\s+", " ").replaceAll(" (AM|PM)$", " $1");
        }
        return trimmed;
    }

    private String extractTimePart(String s) {
        String candidate = s.trim();
        // Common cases: "2026-03-26 08:30", "2026/03/26 08:30 AM", "T08:30:00"
        if (candidate.contains("T")) {
            String[] parts = candidate.split("T", 2);
            if (parts.length == 2) {
                return parts[1].trim();
            }
        }
        if (candidate.contains(" ")) {
            String[] tokens = candidate.split("\\s+");
            // Find the token that looks like a time.
            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i].contains(":")) {
                    if (i + 1 < tokens.length) {
                        String next = tokens[i + 1].toUpperCase(Locale.ENGLISH);
                        if ("AM".equals(next) || "PM".equals(next)) {
                            return tokens[i] + " " + next;
                        }
                    }
                    return tokens[i];
                }
            }
        }
        return candidate;
    }

    /**
     * Build an Excel workbook in memory with the computed payroll summaries.
     */
    public byte[] exportSummariesToExcelBytes(List<PayrollSummaryDTO> summaries) {
        // Ensure deterministic output order. `toList()` can return an unmodifiable list.
        List<PayrollSummaryDTO> sorted = new ArrayList<>(summaries);
        sorted.sort(Comparator.comparing(PayrollSummaryDTO::getName, String.CASE_INSENSITIVE_ORDER));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Payroll Summary");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Total Hours");
            header.createCell(2).setCellValue("Regular Pay");
            header.createCell(3).setCellValue("OT Pay");
            header.createCell(4).setCellValue("Total Salary");

            int rowIndex = 1;
            for (PayrollSummaryDTO summary : sorted) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(summary.getName());
                row.createCell(1).setCellValue(summary.getTotalHours());
                row.createCell(2).setCellValue(summary.getRegularPay());
                row.createCell(3).setCellValue(summary.getOvertimePay());
                row.createCell(4).setCellValue(summary.getTotalSalary());
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                workbook.write(baos);
                return baos.toByteArray();
            }
        } catch (IOException e) {
            throw new PayrollProcessingException("Failed to export results to Excel.", e);
        }
    }
}

