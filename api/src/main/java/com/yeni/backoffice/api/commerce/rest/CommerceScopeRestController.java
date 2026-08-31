package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController @RequestMapping("/admin/api")
public class CommerceScopeRestController {
    private final CommerceBrandRepository brands;private final CommerceStoreRepository stores;private final ProductRepository products;private final ProductCategoryRepository categories;private final StoreProductRepository storeProducts;
    public CommerceScopeRestController(CommerceBrandRepository brands,CommerceStoreRepository stores,ProductRepository products,ProductCategoryRepository categories,StoreProductRepository storeProducts){this.brands=brands;this.stores=stores;this.products=products;this.categories=categories;this.storeProducts=storeProducts;}

    @GetMapping("/brands") public List<BrandResponse> brands(){return brands.findAllByOrderByIdAsc().stream().map(BrandResponse::from).toList();}
    @GetMapping("/brands/{brandId}/stores") public List<StoreScopeResponse> stores(@PathVariable Long brandId){requireBrand(brandId);return stores.findAllByOrderByIdAsc().stream().filter(store->brandId.equals(store.getBrandId())).map(StoreScopeResponse::from).toList();}
    @GetMapping("/brands/{brandId}/categories") public List<CategoryScopeResponse> categories(@PathVariable Long brandId){requireBrand(brandId);return categories.findAllByOrderBySortOrderAscIdAsc().stream().filter(category->brandId.equals(category.getBrandId())).map(CategoryScopeResponse::from).toList();}
    @GetMapping("/brands/{brandId}/products") public List<ProductScopeResponse> products(@PathVariable Long brandId){requireBrand(brandId);return products.findAll().stream().filter(product->brandId.equals(product.getBrandId())).map(ProductScopeResponse::from).toList();}
    @GetMapping("/stores/{storeId}/products") public List<StoreProductResponse> storeProducts(@PathVariable Long storeId){CommerceStore store=requireStore(storeId);return storeProducts.findByStoreIdOrderByIdAsc(storeId).stream().map(setting->{Product product=requireProduct(setting.getProductId());requireSameBrand(store,product);return StoreProductResponse.from(setting,product);}).toList();}
    @PutMapping("/stores/{storeId}/products/{productId}/sales-setting") @Transactional public ResponseEntity<StoreProductResponse> updateSales(@PathVariable Long storeId,@PathVariable Long productId,@Valid @RequestBody StoreProductRequest request){CommerceStore store=requireStore(storeId);Product product=requireProduct(productId);requireSameBrand(store,product);StoreProduct setting=storeProducts.findByStoreIdAndProductId(storeId,productId).orElseGet(()->StoreProduct.builder().storeId(storeId).productId(productId).salePrice(product.getSalePrice()).saleStatus(product.getSaleStatus()).exposed(true).inventoryManaged(product.isInventoryManaged()).build());setting.update(request.salePrice(),request.saleStatus(),request.exposed(),request.inventoryManaged());return ResponseEntity.ok(StoreProductResponse.from(storeProducts.save(setting),product));}
    private CommerceBrand requireBrand(Long id){return brands.findById(id).orElseThrow(()->new IllegalArgumentException("브랜드를 찾을 수 없습니다."));}
    private CommerceStore requireStore(Long id){return stores.findById(id).orElseThrow(()->new IllegalArgumentException("매장을 찾을 수 없습니다."));}
    private Product requireProduct(Long id){return products.findById(id).orElseThrow(()->new IllegalArgumentException("상품을 찾을 수 없습니다."));}
    private void requireSameBrand(CommerceStore store,Product product){if(store.getBrandId()==null||!store.getBrandId().equals(product.getBrandId()))throw new IllegalArgumentException("해당 매장에서 관리할 수 없는 상품입니다.");}
    public record BrandResponse(Long id,String brandCode,String brandName,String businessType,boolean active){static BrandResponse from(CommerceBrand value){return new BrandResponse(value.getId(),value.getBrandCode(),value.getBrandName(),value.getBusinessType().name(),value.isActive());}}
    public record StoreScopeResponse(Long id,Long brandId,String storeCode,String storeName,String brandName,String businessType,boolean active){static StoreScopeResponse from(CommerceStore value){return new StoreScopeResponse(value.getId(),value.getBrandId(),value.getStoreCode(),value.getStoreName(),value.getBrandName(),value.getBusinessType().name(),value.isActive());}}
    public record CategoryScopeResponse(Long id,Long brandId,String categoryName,int sortOrder,boolean exposed){static CategoryScopeResponse from(ProductCategory value){return new CategoryScopeResponse(value.getId(),value.getBrandId(),value.getCategoryName(),value.getSortOrder(),value.isExposed());}}
    public record ProductScopeResponse(Long id,Long brandId,String productCode,String productName,String category,BigDecimal basePrice){static ProductScopeResponse from(Product value){return new ProductScopeResponse(value.getId(),value.getBrandId(),value.getProductCode(),value.getProductName(),value.getCategory(),value.getSalePrice());}}
    public record StoreProductRequest(@NotNull @Positive BigDecimal salePrice,@NotNull ProductSaleStatus saleStatus,boolean exposed,boolean inventoryManaged){}
    public record StoreProductResponse(Long id,Long storeId,Long productId,String productCode,String productName,BigDecimal basePrice,BigDecimal salePrice,String saleStatus,boolean exposed,boolean inventoryManaged){static StoreProductResponse from(StoreProduct setting,Product product){return new StoreProductResponse(setting.getId(),setting.getStoreId(),product.getId(),product.getProductCode(),product.getProductName(),product.getSalePrice(),setting.getSalePrice(),setting.getSaleStatus().name(),setting.isExposed(),setting.isInventoryManaged());}}
}
