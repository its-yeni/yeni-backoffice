package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceBrandRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDateTime;

@RestController @RequestMapping("/admin/api/commerce/stores")
public class CommerceStoreRestController {
    private final CommerceStoreRepository stores;
    private final CommerceBrandRepository brands;
    public CommerceStoreRestController(CommerceStoreRepository stores,CommerceBrandRepository brands){this.stores=stores;this.brands=brands;}

    @GetMapping public List<StoreResponse> all(){return stores.findAllByOrderByIdAsc().stream().map(StoreResponse::from).toList();}
    @PostMapping @Transactional public ResponseEntity<StoreResponse> create(@Valid @RequestBody StoreRequest request){
        if(stores.findByStoreCode(request.storeCode()).isPresent())return ResponseEntity.badRequest().build();
        var brand=brands.findById(request.brandId()).orElseThrow(()->new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
        CommerceStore store=stores.save(CommerceStore.builder().brandId(brand.getId()).storeCode(request.storeCode()).storeName(request.storeName()).businessType(brand.getBusinessType()).brandName(brand.getBrandName()).description(request.description()).active(request.active()).build());
        return ResponseEntity.ok(StoreResponse.from(store));
    }
    @PutMapping("/{id}") @Transactional public ResponseEntity<StoreResponse> update(@PathVariable Long id,@Valid @RequestBody StoreRequest request){
        var brand=brands.findById(request.brandId()).orElseThrow(()->new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
        return stores.findById(id).map(store->{store.update(brand.getId(),request.storeName(),brand.getBusinessType(),brand.getBrandName(),request.description(),request.active());return ResponseEntity.ok(StoreResponse.from(store));}).orElseGet(()->ResponseEntity.notFound().build());
    }

    public record StoreRequest(@NotNull Long brandId,@NotBlank String storeCode,@NotBlank String storeName,@NotBlank String description,boolean active){}
    public record StoreResponse(Long id,Long brandId,String storeCode,String storeName,StoreBusinessType businessType,String brandName,String description,boolean active,LocalDateTime updatedAt){static StoreResponse from(CommerceStore store){return new StoreResponse(store.getId(),store.getBrandId(),store.getStoreCode(),store.getStoreName(),store.getBusinessType(),store.getBrandName(),store.getDescription(),store.isActive(),store.getUpdatedAt());}}
}
