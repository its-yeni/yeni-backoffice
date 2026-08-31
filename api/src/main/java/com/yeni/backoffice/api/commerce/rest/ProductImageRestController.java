package com.yeni.backoffice.api.commerce.rest;
import com.yeni.backoffice.api.commerce.service.ProductImageStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
@RestController @RequestMapping("/admin/api/commerce/product-images")
public class ProductImageRestController {
    private final ProductImageStorageService storage;
    public ProductImageRestController(ProductImageStorageService storage){this.storage=storage;}
    @PostMapping public ResponseEntity<Map<String,String>> upload(@RequestPart("file") MultipartFile file){return ResponseEntity.ok(Map.of("imageUrl",storage.store(file)));}
}
