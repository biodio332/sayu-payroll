package com.sayu.payroll.dto;

/**
 * Response DTO returned by the payroll processing endpoints.
 */
public class PayrollSummaryDTO {
    private String name;
    private double totalHours;
    private double regularPay;
    private double overtimePay;
    private double totalSalary;

    public PayrollSummaryDTO() {
    }

    public PayrollSummaryDTO(String name, double totalHours, double regularPay, double overtimePay, double totalSalary) {
        this.name = name;
        this.totalHours = totalHours;
        this.regularPay = regularPay;
        this.overtimePay = overtimePay;
        this.totalSalary = totalSalary;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(double totalHours) {
        this.totalHours = totalHours;
    }

    public double getRegularPay() {
        return regularPay;
    }

    public void setRegularPay(double regularPay) {
        this.regularPay = regularPay;
    }

    public double getOvertimePay() {
        return overtimePay;
    }

    public void setOvertimePay(double overtimePay) {
        this.overtimePay = overtimePay;
    }

    public double getTotalSalary() {
        return totalSalary;
    }

    public void setTotalSalary(double totalSalary) {
        this.totalSalary = totalSalary;
    }
}

