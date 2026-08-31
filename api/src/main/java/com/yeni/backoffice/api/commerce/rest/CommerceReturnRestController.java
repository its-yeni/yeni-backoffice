package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.CommerceReturnDtos.CompleteRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceReturnDtos.RejectRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceReturnDtos.ReturnCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceReturnDtos.ReturnResponse;
import com.yeni.backoffice.core.commerce.service.CommerceReturnService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api/commerce/returns")
public class CommerceReturnRestController {
    private final CommerceReturnService service;
    public CommerceReturnRestController(CommerceReturnService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<ReturnResponse>> list(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.list(status, storeId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<ReturnResponse>> byOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(service.listByOrder(orderId));
    }

    @PostMapping
    public ResponseEntity<ReturnResponse> requestReturn(@Valid @RequestBody ReturnCreateRequest request,
                                                        @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.requestReturn(request, storeId));
    }

    @PostMapping("/{id}/inspect")
    public ResponseEntity<ReturnResponse> startInspection(@PathVariable Long id,
                                                           @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.startInspection(id, storeId));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ReturnResponse> complete(@PathVariable Long id, @RequestBody(required = false) CompleteRequest request,
                                                    @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.completeInspection(id, request == null ? null : request.returnShippingFee(), storeId));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ReturnResponse> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request,
                                                  @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.reject(id, request.reason(), storeId));
    }

    @PostMapping("/{id}/retry-refund")
    public ResponseEntity<ReturnResponse> retryRefund(@PathVariable Long id,
                                                       @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.retryRefund(id, storeId));
    }
}
