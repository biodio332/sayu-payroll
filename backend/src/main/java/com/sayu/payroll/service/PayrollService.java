package com.sayu.payroll.service;

import com.sayu.payroll.dto.PayrollSummaryDTO;
import com.sayu.payroll.exception.PayrollProcessingException;
import com.sayu.payroll.util.ExcelPayrollParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayrollService {

    private static final double DAILY_RATE = 501.0;
    private static final double HOURLY_RATE = 62.625;
    private static final double OT_RATE = 78.28; // HOURLY_RATE * 1.25 as requested.

    // Shift rules (24-hour time).
    // Shift 1: 09:00 AM – 6:00 PM, Break: 13:00 – 14:00, Late if Time In >= 09:15
    // Shift 2: 01:00 PM – 10:00 PM, Break: 17:00 – 18:00, Late if Time In >= 01:05
    private static final java.time.LocalTime SHIFT1_START = java.time.LocalTime.of(9, 0);
    private static final java.time.LocalTime SHIFT1_END = java.time.LocalTime.of(18, 0);
    private static final java.time.LocalTime SHIFT1_LATE_THRESHOLD = java.time.LocalTime.of(9, 15);

    private static final java.time.LocalTime SHIFT2_START = java.time.LocalTime.of(13, 0);
    private static final java.time.LocalTime SHIFT2_END = java.time.LocalTime.of(22, 0);
    private static final java.time.LocalTime SHIFT2_LATE_THRESHOLD = java.time.LocalTime.of(13, 5);

    private static final int BREAK_MINUTES = 60;
    private static final int OVERTIME_MINUTES_THRESHOLD = 30;

    private final ExcelPayrollParser excelPayrollParser;

    public PayrollService(ExcelPayrollParser excelPayrollParser) {
        this.excelPayrollParser = excelPayrollParser;
    }

    public List<PayrollSummaryDTO> processUploadedExcel(MultipartFile file) {
        // `stream().toList()` returns an immutable list, so we sort via stream instead of in-place sort.
        return computeSummaries(file).stream()
                .sorted(Comparator.comparing(PayrollSummaryDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public byte[] exportSummariesToExcel(MultipartFile file) {
        List<PayrollSummaryDTO> summaries = computeSummaries(file);
        return excelPayrollParser.exportSummariesToExcelBytes(summaries);
    }

    private List<PayrollSummaryDTO> computeSummaries(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PayrollProcessingException("Empty file. Please upload a valid Excel file.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new PayrollProcessingException("Invalid file. Missing filename.");
        }

        String lower = originalName.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new PayrollProcessingException("Invalid file type. Only .xls/.xlsx are supported.");
        }

        List<ExcelPayrollParser.EmployeeTimeLog> timeLogs = parseExcel(file);

        // Group by employee name and sum hours/pay across all parsed shift pairs.
        Map<String, EmployeeTotals> totals = new HashMap<>();
        for (ExcelPayrollParser.EmployeeTimeLog log : timeLogs) {
            ShiftBreakdown shift = calculateShift(log);
            SalaryBreakdown salary = calculateSalary(shift);

            totals.computeIfAbsent(log.name(), k -> new EmployeeTotals())
                    .add(salary);
        }

        return totals.entrySet().stream()
                .map(e -> {
                    EmployeeTotals t = e.getValue();
                    return new PayrollSummaryDTO(
                            e.getKey(),
                            t.totalHours,
                            t.regularPay,
                            t.overtimePay,
                            t.totalSalary
                    );
                })
                .toList();
    }

    private List<ExcelPayrollParser.EmployeeTimeLog> parseExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return excelPayrollParser.parseAttendanceRows(is);
        } catch (IOException e) {
            throw new PayrollProcessingException("Failed to read uploaded file.", e);
        }
    }

    private enum ShiftType { SHIFT1, SHIFT2 }

    private record ShiftBreakdown(int regularMinutes, int overtimeMinutes, boolean late) {
        double totalHours() {
            return (regularMinutes + overtimeMinutes) / 60.0;
        }
    }

    private record SalaryBreakdown(double regularPay, double overtimePay, double totalSalary, double totalHours) {}

    private static class EmployeeTotals {
        double totalHours;
        double regularPay;
        double overtimePay;
        double totalSalary;

        void add(SalaryBreakdown salary) {
            totalHours += salary.totalHours();
            regularPay += salary.regularPay();
            overtimePay += salary.overtimePay();
            totalSalary += salary.totalSalary();
        }
    }

    private ShiftBreakdown calculateShift(ExcelPayrollParser.EmployeeTimeLog log) {
        java.time.LocalTime timeIn = log.timeIn();
        java.time.LocalTime timeOut = log.timeOut();

        // Business rule: if TimeOut < TimeIn, treat as overnight and add 24 hours.
        int inMin = toMinutes(timeIn);
        int outMin = toMinutes(timeOut);
        if (outMin < inMin) {
            outMin += 24 * 60;
        }

        ShiftType shiftType = timeIn.isBefore(SHIFT2_START) ? ShiftType.SHIFT1 : ShiftType.SHIFT2;
        java.time.LocalTime shiftEnd = shiftType == ShiftType.SHIFT1 ? SHIFT1_END : SHIFT2_END;
        java.time.LocalTime lateThreshold = shiftType == ShiftType.SHIFT1 ? SHIFT1_LATE_THRESHOLD : SHIFT2_LATE_THRESHOLD;

        boolean isLate = !timeIn.isBefore(lateThreshold); // >= late threshold.

        int shiftEndMin = toMinutes(shiftEnd);
        if (shiftEndMin < inMin) {
            shiftEndMin += 24 * 60; // defensive; should not happen for expected shift windows.
        }

        // Raw minutes from Time In to Time Out (overnight-adjusted).
        int rawMinutes = outMin - inMin;

        // Split into "within shift end" vs "beyond shift end" so overtime rule is applied correctly.
        int minutesUpToShiftEnd = Math.max(0, Math.min(outMin, shiftEndMin) - inMin);
        int overtimeMinutesRaw = Math.max(0, outMin - shiftEndMin);

        // Break deduction: deduct 1 hour if shift is completed (TimeOut >= shift end).
        boolean shiftCompleted = outMin >= shiftEndMin;
        int minutesAfterBreak = minutesUpToShiftEnd;
        if (shiftCompleted) {
            minutesAfterBreak = Math.max(0, minutesUpToShiftEnd - BREAK_MINUTES);
        }

        // Overtime rule:
        // - overtime minutes count only if exceeded by at least 30 minutes beyond shift end
        // - only minutes beyond shift end count as OT
        int overtimeMinutes;
        int regularMinutes;
        if (overtimeMinutesRaw >= OVERTIME_MINUTES_THRESHOLD) {
            overtimeMinutes = overtimeMinutesRaw;
            // All minutes beyond shift end are OT; regular is only within-shift-end minutes (after break).
            regularMinutes = minutesAfterBreak;
        } else {
            overtimeMinutes = 0;
            // Excess beyond shift end counts as regular if it doesn't meet the 30-minute threshold.
            regularMinutes = minutesAfterBreak + overtimeMinutesRaw;
        }

        // If something went very wrong (negative due to bad data), clamp.
        if (regularMinutes < 0) regularMinutes = 0;
        if (overtimeMinutes < 0) overtimeMinutes = 0;

        // Sanity: total minutes should not exceed raw minutes by more than the break rule.
        // (Break is only deducted when completed, so this should be safe.)
        if (regularMinutes + overtimeMinutes > rawMinutes + BREAK_MINUTES) {
            // don't throw for MVP; just clamp.
            double totalHours = (rawMinutes) / 60.0;
            int totalMinutesClamped = (int) Math.round(totalHours * 60);
            regularMinutes = Math.max(0, totalMinutesClamped - overtimeMinutes);
        }

        return new ShiftBreakdown(regularMinutes, overtimeMinutes, isLate);
    }

    private SalaryBreakdown calculateSalary(ShiftBreakdown shift) {
        double regularPay = applyLatePenalty(shift.regularMinutes(), shift.late());
        double overtimePay = computeOvertime(shift.overtimeMinutes());
        double totalSalary = regularPay + overtimePay;
        double totalHours = shift.totalHours();
        return new SalaryBreakdown(regularPay, overtimePay, totalSalary, totalHours);
    }

    private double applyLatePenalty(int regularMinutes, boolean late) {
        // Late rule:
        // If late: first hour is paid at 50% only. Remaining regular hours are paid at 100%.
        int discountableMinutes = 0;
        if (late) {
            discountableMinutes = Math.min(BREAK_MINUTES, regularMinutes); // "first hour" => 60 minutes
        }

        double discountedHours = discountableMinutes / 60.0;
        double fullHours = (regularMinutes - discountableMinutes) / 60.0;

        return (discountedHours * HOURLY_RATE * 0.5) + (fullHours * HOURLY_RATE);
    }

    private double computeOvertime(int overtimeMinutes) {
        // OT is computed only when overtime minutes have already qualified via the 30-minute rule.
        return (overtimeMinutes / 60.0) * OT_RATE;
    }

    private int toMinutes(java.time.LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }
}

