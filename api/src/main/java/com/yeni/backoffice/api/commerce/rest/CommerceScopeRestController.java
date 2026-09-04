package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.service.CommerceScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/admin/api")
public class CommerceScopeRestController {
    private final CommerceScopeService scopeService;

    public CommerceScopeRestController(CommerceScopeService scopeService) {
        this.scopeService = scopeService;
    }

    @GetMapping("/brands")
    public List<BrandResponse> brands() {
        return scopeService.getBrands().stream().map(BrandResponse::from).toList();
    }

    @GetMapping("/brands/{brandId}/stores")
    public List<StoreScopeResponse> stores(@PathVariable Long brandId) {
        return scopeService.getStores(brandId).stream().map(StoreScopeResponse::from).toList();
    }

    @GetMapping("/brands/{brandId}/categories")
    public List<CategoryScopeResponse> categories(@PathVariable Long brandId) {
        return scopeService.getCategories(brandId).stream().map(CategoryScopeResponse::from).toList();
    }

    @GetMapping("/brands/{brandId}/products")
    public List<ProductScopeResponse> products(@PathVariable Long brandId) {
        return scopeService.getProducts(brandId).stream().map(ProductScopeResponse::from).toList();
    }

    @GetMapping("/stores/{storeId}/products")
    public List<StoreProductResponse> storeProducts(@PathVariable Long storeId) {
        return scopeService.getStoreProducts(storeId).stream()
                .map(value -> StoreProductResponse.from(value.setting(), value.product())).toList();
    }

    @PutMapping("/stores/{storeId}/products/{productId}/sales-setting")
    public ResponseEntity<StoreProductResponse> updateSales(@PathVariable Long storeId, @PathVariable Long productId,
                                                             @Valid @RequestBody StoreProductRequest request) {
        var result = scopeService.updateSalesSetting(storeId, productId, request.salePrice(), request.saleStatus(),
                request.exposed(), request.inventoryManaged());
        return ResponseEntity.ok(StoreProductResponse.from(result.setting(), result.product()));
    }

    public record BrandResponse(Long id, String brandCode, String brandName, String businessType, boolean active) {
        static BrandResponse from(CommerceBrand value) {
            return new BrandResponse(value.getId(), value.getBrandCode(), value.getBrandName(), value.getBusinessType().name(), value.isActive());
        }
    }
    public record StoreScopeResponse(Long id, Long brandId, String storeCode, String storeName, String brandName,
                                     String businessType, boolean active) {
        static StoreScopeResponse from(CommerceStore value) {
            return new StoreScopeResponse(value.getId(), value.getBrandId(), value.getStoreCode(), value.getStoreName(),
                    value.getBrandName(), value.getBusinessType().name(), value.isActive());
        }
    }
    public record CategoryScopeResponse(Long id, Long brandId, String categoryName, int sortOrder, boolean exposed) {
        static CategoryScopeResponse from(ProductCategory value) {
            return new CategoryScopeResponse(value.getId(), value.getBrandId(), value.getCategoryName(), value.getSortOrder(), value.isExposed());
        }
    }
    public record ProductScopeResponse(Long id, Long brandId, String productCode, String productName,
                                       String category, BigDecimal basePrice) {
        static ProductScopeResponse from(Product value) {
            return new ProductScopeResponse(value.getId(), value.getBrandId(), value.getProductCode(),
                    value.getProductName(), value.getCategory(), value.getSalePrice());
        }
    }
    public record StoreProductRequest(@NotNull @Positive BigDecimal salePrice, @NotNull ProductSaleStatus saleStatus,
                                      boolean exposed, boolean inventoryManaged) {}
    public record StoreProductResponse(Long id, Long storeId, Long productId, String productCode, String productName,
                                       BigDecimal basePrice, BigDecimal salePrice, String saleStatus, boolean exposed,
                                       boolean inventoryManaged) {
        static StoreProductResponse from(StoreProduct setting, Product product) {
            return new StoreProductResponse(setting.getId(), setting.getStoreId(), product.getId(), product.getProductCode(),
                    product.getProductName(), product.getSalePrice(), setting.getSalePrice(), setting.getSaleStatus().name(),
                    setting.isExposed(), setting.isInventoryManaged());
        }
    }
}
