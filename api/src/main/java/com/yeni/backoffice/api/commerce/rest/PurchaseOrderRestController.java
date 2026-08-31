package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.PurchaseOrderDtos.*;
import com.yeni.backoffice.core.commerce.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/commerce/purchase-orders")
public class PurchaseOrderRestController {

    private final PurchaseOrderService service;

    public PurchaseOrderRestController(PurchaseOrderService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<PoResponse>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.list(status));
    }

    @GetMapping("/summary")
    public ResponseEntity<PoSummary> summary() { return ResponseEntity.ok(service.summary()); }

    @GetMapping("/{id}")
    public ResponseEntity<PoResponse> detail(@PathVariable Long id) { return ResponseEntity.ok(service.detail(id)); }

    @PostMapping
    public ResponseEntity<PoResponse> create(@Valid @RequestBody PoCreateRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PoResponse> updateHeader(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long supplierId = body.get("supplierId") == null ? null : Long.valueOf(body.get("supplierId").toString());
        Long storeId = body.get("storeId") == null ? null : Long.valueOf(body.get("storeId").toString());
        LocalDate eta = body.get("expectedArrivalDate") == null || body.get("expectedArrivalDate").toString().isBlank()
                ? null : LocalDate.parse(body.get("expectedArrivalDate").toString());
        String memo = body.get("memo") == null ? null : body.get("memo").toString();
        return ResponseEntity.ok(service.updateHeader(id, supplierId, storeId, eta, memo));
    }

    @PostMapping("/{id}/place")
    public ResponseEntity<PoResponse> place(@PathVariable Long id) { return ResponseEntity.ok(service.place(id)); }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PoResponse> cancel(@PathVariable Long id) { return ResponseEntity.ok(service.cancel(id)); }

    @PostMapping("/{id}/receive")
    public ResponseEntity<PoResponse> receive(@PathVariable Long id, @Valid @RequestBody PoReceiveRequest request) {
        return ResponseEntity.ok(service.receive(id, request));
    }
}
