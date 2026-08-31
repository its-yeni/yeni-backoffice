package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.BulkDispatchRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.BulkDispatchResult;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DispatchRequest;
import com.yeni.backoffice.core.commerce.service.CommerceDeliveryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api/commerce/deliveries")
public class CommerceDeliveryRestController {
    private final CommerceDeliveryService service;
    public CommerceDeliveryRestController(CommerceDeliveryService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<DeliveryResponse>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.list(status, storeId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResponse> byOrder(@PathVariable Long orderId) {
        DeliveryResponse response = service.findByOrderId(orderId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/dispatch")
    public ResponseEntity<DeliveryResponse> dispatch(@PathVariable Long id, @Valid @RequestBody DispatchRequest request,
                                                      @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.dispatch(id, request.carrier(), request.trackingNumber(), storeId));
    }

    @PostMapping("/bulk-dispatch")
    public ResponseEntity<List<BulkDispatchResult>> bulkDispatch(@Valid @RequestBody BulkDispatchRequest request,
                                                                  @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.bulkDispatch(request.rows(), storeId));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<DeliveryResponse> complete(@PathVariable Long id,
                                                      @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.complete(id, storeId));
    }

    // 반송 처리는 더 이상 여기서 단독으로 하지 않는다 — 반송만 찍고 재고/환불이 안 맞는 상태가 되는 걸 막기 위해,
    // "반품 관리"(CommerceReturnRestController)의 검수 완료 처리 안에서만 배송을 반송 상태로 바꾼다.
}
