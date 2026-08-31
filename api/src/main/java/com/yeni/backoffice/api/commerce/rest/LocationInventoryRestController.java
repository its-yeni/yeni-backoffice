package com.yeni.backoffice.api.commerce.rest;
import com.yeni.backoffice.core.commerce.dto.LocationInventoryDtos.LocationInventoryResponse;
import com.yeni.backoffice.core.commerce.service.LocationInventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/admin/api/commerce/location-inventory")
public class LocationInventoryRestController {
    private final LocationInventoryService service;
    public LocationInventoryRestController(LocationInventoryService s) { service = s; }

    @GetMapping
    public ResponseEntity<List<LocationInventoryResponse>> list(@RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.list(storeId));
    }

    @PatchMapping("/{id}/safety-stock")
    public ResponseEntity<Void> updateSafetyStock(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.updateSafetyStock(id, body.getOrDefault("safetyStock", 0));
        return ResponseEntity.noContent().build();
    }
}
