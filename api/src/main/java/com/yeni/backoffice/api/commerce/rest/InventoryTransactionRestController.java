package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.InventoryTransactionDtos.InventoryTransactionResponse;
import com.yeni.backoffice.core.commerce.service.InventoryTransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/api/commerce/inventory-transactions")
public class InventoryTransactionRestController {
    private final InventoryTransactionService service;
    public InventoryTransactionRestController(InventoryTransactionService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<InventoryTransactionResponse>> list(@RequestParam(required = false) Long variantId,
            @RequestParam(required = false) String type) {
        if (variantId != null) return ResponseEntity.ok(service.listByVariant(variantId));
        if (type != null && !type.isBlank()) {
            try {
                return ResponseEntity.ok(service.listRecentByType(com.yeni.backoffice.core.commerce.enums.InventoryTransactionType.valueOf(type)));
            } catch (IllegalArgumentException ignored) { /* 알 수 없는 유형이면 전체 조회로 폴백 */ }
        }
        return ResponseEntity.ok(service.listRecent());
    }
}
