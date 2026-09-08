package com.yeni.backoffice.api.payment.rest;

import com.yeni.backoffice.core.payment.dto.IntegratedSalesDtos.IntegratedSalesResponse;
import com.yeni.backoffice.core.payment.service.IntegratedSalesQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/api/integrated-sales")
public class IntegratedSalesRestController {

    private final IntegratedSalesQueryService service;

    public IntegratedSalesRestController(IntegratedSalesQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<IntegratedSalesResponse> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stage) {
        return ResponseEntity.ok(service.query(startDate, endDate, storeId, keyword, stage));
    }
}
