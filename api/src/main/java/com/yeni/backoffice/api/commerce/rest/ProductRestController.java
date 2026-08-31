package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.ProductDtos.*;
import com.yeni.backoffice.core.commerce.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/commerce/products")
public class ProductRestController {
    private final ProductService productService;
    public ProductRestController(ProductService productService) { this.productService = productService; }

    @GetMapping
    public ResponseEntity<ProductPageResponse> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String saleStatus,
            @RequestParam(required = false) String storeCode,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(productService.search(keyword, saleStatus, storeCode, page, size));
    }
    @PostMapping public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductSaveRequest request) { return ResponseEntity.ok(productService.create(request)); }
    @PutMapping("/{id}") public ResponseEntity<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest request) { return ResponseEntity.ok(productService.update(id, request)); }
    @PatchMapping("/{id}/status") public ResponseEntity<ProductResponse> status(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) { return ResponseEntity.ok(productService.changeStatus(id, request.saleStatus())); }
}
