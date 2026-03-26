package com.sayu.payroll.controller;

import com.sayu.payroll.dto.PayrollSummaryDTO;
import com.sayu.payroll.service.PayrollService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<PayrollSummaryDTO> upload(@RequestParam("file") MultipartFile file) {
        return payrollService.processUploadedExcel(file);
    }

    @PostMapping(value = "/upload/export", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> export(@RequestParam("file") MultipartFile file) {
        byte[] excelBytes = payrollService.exportSummariesToExcel(file);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("payroll-results.xlsx").build().toString()
        );
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }
}

