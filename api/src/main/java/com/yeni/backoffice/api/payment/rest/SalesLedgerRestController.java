package com.yeni.backoffice.api.payment.rest;

import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesLedgerLinksResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesLedgerPageResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesLedgerSummaryResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesResponse;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.PendingSalesResponse;
import com.yeni.backoffice.core.commerce.service.CommerceDeliveryService;
import com.yeni.backoffice.core.payment.service.SalesLedgerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/sales-ledger")
public class SalesLedgerRestController {

    private final SalesLedgerService salesLedgerService;
    private final CommerceDeliveryService commerceDeliveryService;

    public SalesLedgerRestController(SalesLedgerService salesLedgerService, CommerceDeliveryService commerceDeliveryService) {
        this.salesLedgerService = salesLedgerService;
        this.commerceDeliveryService = commerceDeliveryService;
    }

    /** 미확정 매출 목록(분류·상품 요약 포함). */
    @GetMapping("/pending")
    public ResponseEntity<PendingSalesResponse> pending(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(salesLedgerService.getPendingSales(startDate, endDate, storeId, keyword));
    }

    /** 운영자 수동 구매 확정 — 해당 주문의 미확정 매출을 정산 대상으로 전환한다. */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestBody Map<String, String> body) {
        String orderNo = body.get("orderNo");
        commerceDeliveryService.forceConfirmPurchase(orderNo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<SalesLedgerPageResponse> ledger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String ledgerStatus,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean confirmedYn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(salesLedgerService.getSalesLedger(
                startDate, endDate, transactionType, ledgerStatus, settlementStatus, keyword, page, size, storeId, confirmedYn));
    }

    @GetMapping("/summary")
    public ResponseEntity<SalesLedgerSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String ledgerStatus,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(salesLedgerService.getSalesLedgerSummary(
                startDate, endDate, transactionType, ledgerStatus, settlementStatus, keyword, storeId));
    }

    @GetMapping("/{salesTransactionId}")
    public ResponseEntity<SalesResponse> detail(@PathVariable Long salesTransactionId) {
        return ResponseEntity.ok(salesLedgerService.getSalesLedgerDetail(salesTransactionId));
    }

    @GetMapping("/{salesTransactionId}/links")
    public ResponseEntity<SalesLedgerLinksResponse> links(@PathVariable Long salesTransactionId) {
        return ResponseEntity.ok(salesLedgerService.getSalesLedgerLinks(salesTransactionId));
    }
}
