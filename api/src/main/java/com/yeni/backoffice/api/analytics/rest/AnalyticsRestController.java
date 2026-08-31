package com.yeni.backoffice.api.analytics.rest;

import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.AnalyticsFilter;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.InventoryAnalyticsRow;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.OrderAnalyticsRow;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.OverviewResponse;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.PaymentAnalyticsRow;
import com.yeni.backoffice.core.analytics.dto.AnalyticsDtos.SettlementAnalyticsRow;
import com.yeni.backoffice.core.analytics.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Power BI(Web Connector / Power Query) 가 호출하는 <b>읽기 전용</b> 분석 API.
 * 관리자 업무 API(/admin/api/**)와 분리되어 있으며 파라미터가 없으면 최근 30일을 반환한다.
 *
 * <pre>
 * GET /api/analytics/overview?startDate=&endDate=&channel=&storeId=&paymentMethod=
 * GET /api/analytics/orders
 * GET /api/analytics/payments
 * GET /api/analytics/settlements
 * GET /api/analytics/inventory
 * </pre>
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsRestController {

    private final AnalyticsService analyticsService;

    public AnalyticsRestController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<OverviewResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String paymentMethod) {
        return ResponseEntity.ok(analyticsService.overview(
                AnalyticsFilter.of(startDate, endDate, channel, storeId, paymentMethod)));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderAnalyticsRow>> orders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String paymentMethod) {
        return ResponseEntity.ok(analyticsService.orders(
                AnalyticsFilter.of(startDate, endDate, channel, storeId, paymentMethod)));
    }

    @GetMapping("/payments")
    public ResponseEntity<List<PaymentAnalyticsRow>> payments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String paymentMethod) {
        return ResponseEntity.ok(analyticsService.payments(
                AnalyticsFilter.of(startDate, endDate, channel, storeId, paymentMethod)));
    }

    @GetMapping("/settlements")
    public ResponseEntity<List<SettlementAnalyticsRow>> settlements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String paymentMethod) {
        return ResponseEntity.ok(analyticsService.settlements(
                AnalyticsFilter.of(startDate, endDate, channel, storeId, paymentMethod)));
    }

    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryAnalyticsRow>> inventory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String paymentMethod) {
        return ResponseEntity.ok(analyticsService.inventory(
                AnalyticsFilter.of(startDate, endDate, channel, storeId, paymentMethod)));
    }
}
