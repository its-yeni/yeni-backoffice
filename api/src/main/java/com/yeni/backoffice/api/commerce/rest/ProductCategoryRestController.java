package com.yeni.backoffice.api.commerce.rest;
import com.yeni.backoffice.core.commerce.dto.ProductCategoryDtos.*;
import com.yeni.backoffice.core.commerce.service.ProductCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/admin/api/commerce/categories")
public class ProductCategoryRestController {
    private final ProductCategoryService service;
    public ProductCategoryRestController(ProductCategoryService service){this.service=service;}
    @GetMapping public ResponseEntity<List<CategoryResponse>> all(@RequestParam(required=false) String storeCode){return ResponseEntity.ok(service.getAll(storeCode));}
    @PostMapping public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest r){return ResponseEntity.ok(service.create(r));}
    @PutMapping("/{id}") public ResponseEntity<CategoryResponse> update(@PathVariable Long id,@Valid @RequestBody CategoryRequest r){return ResponseEntity.ok(service.update(id,r));}
    @PatchMapping("/{id}/order") public ResponseEntity<List<CategoryResponse>> move(@PathVariable Long id,@RequestBody Map<String,String> body){return ResponseEntity.ok(service.move(id,body.getOrDefault("direction","DOWN")));}
}
