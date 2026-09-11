package com.yeni.backoffice.api.bi.rest;

import com.yeni.backoffice.core.bi.dto.BiDtos.BiFilter;
import com.yeni.backoffice.core.bi.dto.BiDtos.ChannelSalesRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.DailySalesRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.InventoryStatusRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.PaymentStatusRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.ProductSalesRow;
import com.yeni.backoffice.core.bi.service.BiAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Power BI Desktop(Web Connector / Power Query)가 JSON 으로 읽어가는 <b>BI 전용 조회 API</b>.
 * 운영 API(/admin/api/**)와 완전히 분리돼 있고, 페이지네이션 없이 집계 결과를 반환한다.
 * 조회 조건: {@code from, to}(미지정 시 최근 90일, 최대 366일), 일부 API 는 {@code category, channel}.
 *
 * <pre>
 * GET /api/bi/sales/daily?from=2026-01-01&amp;to=2026-12-31&amp;category=&amp;channel=
 * GET /api/bi/sales/products
 * GET /api/bi/sales/stores
 * GET /api/bi/inventory/status?category=
 * GET /api/bi/payment/status?channel=
 * </pre>
 */
@RestController
@RequestMapping("/api/bi")
public class BiAnalyticsController {

    private final BiAnalyticsService service;

    public BiAnalyticsController(BiAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/sales/daily")
    public ResponseEntity<List<DailySalesRow>> salesDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String channel) {
        BiFilter filter = service.filter(from, to, category, channel);
        return ResponseEntity.ok(service.dailySales(filter));
    }

    @GetMapping("/sales/products")
    public ResponseEntity<List<ProductSalesRow>> salesProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String channel) {
        BiFilter filter = service.filter(from, to, category, channel);
        return ResponseEntity.ok(service.productSales(filter));
    }

    @GetMapping("/sales/stores")
    public ResponseEntity<List<ChannelSalesRow>> salesStores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String channel) {
        BiFilter filter = service.filter(from, to, category, channel);
        return ResponseEntity.ok(service.channelSales(filter));
    }

    @GetMapping("/inventory/status")
    public ResponseEntity<List<InventoryStatusRow>> inventoryStatus(
            @RequestParam(required = false) String category) {
        BiFilter filter = service.filter(null, null, category, null);
        return ResponseEntity.ok(service.inventoryStatus(filter));
    }

    @GetMapping("/payment/status")
    public ResponseEntity<List<PaymentStatusRow>> paymentStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String channel) {
        BiFilter filter = service.filter(from, to, null, channel);
        return ResponseEntity.ok(service.paymentStatus(filter));
    }
}
