package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.SupplierDtos.*;
import com.yeni.backoffice.core.commerce.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/commerce/suppliers")
public class SupplierRestController {

    private final SupplierService service;

    public SupplierRestController(SupplierService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> list() { return ResponseEntity.ok(service.list()); }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierSaveRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> update(@PathVariable Long id, @Valid @RequestBody SupplierSaveRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<SupplierResponse> changeActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(service.changeActive(id, Boolean.TRUE.equals(body.get("active"))));
    }
}
