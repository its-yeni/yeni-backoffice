package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.StockCountDtos.*;
import com.yeni.backoffice.core.commerce.service.StockCountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api/commerce/stock-counts")
public class StockCountRestController {

    private final StockCountService service;

    public StockCountRestController(StockCountService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<StockCountResponse>> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/{id}")
    public ResponseEntity<StockCountResponse> detail(@PathVariable Long id) { return ResponseEntity.ok(service.detail(id)); }

    @PostMapping
    public ResponseEntity<StockCountResponse> start(@Valid @RequestBody StockCountStartRequest request) {
        return ResponseEntity.ok(service.start(request.storeId(), request.scope(), request.memo()));
    }

    @PutMapping("/{id}/lines/{lineId}")
    public ResponseEntity<StockCountResponse> enterCount(@PathVariable Long id, @PathVariable Long lineId,
            @Valid @RequestBody StockCountLineInput input) {
        return ResponseEntity.ok(service.enterCount(id, lineId, input.countedQuantity()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<StockCountResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(service.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<StockCountResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }
}
