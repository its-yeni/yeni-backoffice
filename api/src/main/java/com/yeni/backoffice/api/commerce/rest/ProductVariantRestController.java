package com.yeni.backoffice.api.commerce.rest;
import com.yeni.backoffice.core.commerce.dto.ProductVariantDtos.*;
import com.yeni.backoffice.core.commerce.service.ProductVariantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/admin/api/commerce")
public class ProductVariantRestController{private final ProductVariantService service;public ProductVariantRestController(ProductVariantService s){service=s;}
 @GetMapping("/products/{productId}/variants")public ResponseEntity<List<VariantResponse>> list(@PathVariable Long productId){return ResponseEntity.ok(service.list(productId));}
 @PostMapping("/products/{productId}/variants/generate")public ResponseEntity<List<VariantResponse>> generate(@PathVariable Long productId){return ResponseEntity.ok(service.generate(productId));}
 @PutMapping("/variants/{id}")public ResponseEntity<VariantResponse> update(@PathVariable Long id,@Valid @RequestBody VariantUpdateRequest r){return ResponseEntity.ok(service.update(id,r));}
 @PostMapping("/variants/{id}/receive")public ResponseEntity<VariantResponse> receive(@PathVariable Long id,@Valid @RequestBody VariantReceiveRequest r){return ResponseEntity.ok(service.receive(id,r));}
 @PostMapping("/variants/{id}/adjust")public ResponseEntity<VariantResponse> adjust(@PathVariable Long id,@Valid @RequestBody VariantAdjustRequest r){return ResponseEntity.ok(service.adjust(id,r));}
 @GetMapping("/variants/by-barcode/{barcode}")public ResponseEntity<VariantResponse> byBarcode(@PathVariable String barcode){return ResponseEntity.ok(service.findByBarcode(barcode));}
 @GetMapping("/variants/inventory")public ResponseEntity<List<com.yeni.backoffice.core.commerce.dto.ProductVariantDtos.VariantInventoryResponse>> inventory(@RequestParam(required=false) String storeCode){return ResponseEntity.ok(service.listAllInventory(storeCode));}
}
