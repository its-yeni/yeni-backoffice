package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.ShipmentDtos.ShipmentPendingResponse;
import com.yeni.backoffice.core.commerce.service.ShipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/api/commerce/shipments")
public class ShipmentRestController {
    private final ShipmentService service;
    public ShipmentRestController(ShipmentService service) { this.service = service; }

    @GetMapping("/pending")
    public ResponseEntity<List<ShipmentPendingResponse>> pending(@RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.listPending(storeId));
    }

    @PostMapping("/{orderItemId}/complete")
    public ResponseEntity<Void> complete(@PathVariable Long orderItemId,
                                         @RequestParam(required = false) Long storeId) {
        service.completeShipment(orderItemId, storeId);
        return ResponseEntity.ok().build();
    }
}
