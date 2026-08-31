package com.yeni.backoffice.api.payment.rest;

import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.LedgerConsistencyReport;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.SalesBreakdownResponse;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.SalesSummaryResponse;
import com.yeni.backoffice.core.payment.service.SalesAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/api/sales-analytics")
public class SalesAnalyticsRestController {

    private final SalesAnalyticsService salesAnalyticsService;

    public SalesAnalyticsRestController(SalesAnalyticsService salesAnalyticsService) {
        this.salesAnalyticsService = salesAnalyticsService;
    }

    /** 분류별/상품별 매출 집계. confirmedYn=true 로 확정(정산 대상) 매출만 볼 수 있다. */
    @GetMapping("/breakdown")
    public ResponseEntity<SalesBreakdownResponse> breakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Boolean confirmedYn) {
        return ResponseEntity.ok(salesAnalyticsService.breakdown(startDate, endDate, storeId, confirmedYn));
    }

    /** 매출 분석 상단 요약 지표(총 순매출·주문건수·평균 주문금액·취소율)와 일자별 매출 추이. */
    @GetMapping("/summary")
    public ResponseEntity<SalesSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Boolean confirmedYn) {
        return ResponseEntity.ok(salesAnalyticsService.summary(startDate, endDate, storeId, confirmedYn));
    }

    /** 주문 → 매출 헤더 → 매출 명세 → 정산서 금액 항등식 검산. issues 가 비어 있으면 정합성 OK. */
    @GetMapping("/consistency")
    public ResponseEntity<LedgerConsistencyReport> consistency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(salesAnalyticsService.consistencyReport(startDate, endDate));
    }
}
