package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CommerceScopeService {
    private final CommerceBrandRepository brands;
    private final CommerceStoreRepository stores;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final StoreProductRepository storeProducts;

    public CommerceScopeService(CommerceBrandRepository brands, CommerceStoreRepository stores,
                                ProductRepository products, ProductCategoryRepository categories,
                                StoreProductRepository storeProducts) {
        this.brands = brands;
        this.stores = stores;
        this.products = products;
        this.categories = categories;
        this.storeProducts = storeProducts;
    }

    public List<CommerceBrand> getBrands() { return brands.findAllByOrderByIdAsc(); }

    public List<CommerceStore> getStores(Long brandId) {
        requireBrand(brandId);
        return stores.findAllByOrderByIdAsc().stream().filter(value -> brandId.equals(value.getBrandId())).toList();
    }

    public List<ProductCategory> getCategories(Long brandId) {
        requireBrand(brandId);
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().filter(value -> brandId.equals(value.getBrandId())).toList();
    }

    public List<Product> getProducts(Long brandId) {
        requireBrand(brandId);
        return products.findAll().stream().filter(value -> brandId.equals(value.getBrandId())).toList();
    }

    public List<StoreProductSetting> getStoreProducts(Long storeId) {
        CommerceStore store = requireStore(storeId);
        List<StoreProduct> settings = storeProducts.findByStoreIdOrderByIdAsc(storeId);
        Map<Long, Product> productById = products.findAllById(
                        settings.stream().map(StoreProduct::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return settings.stream().map(setting -> {
            Product product = productById.get(setting.getProductId());
            if (product == null) throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
            requireSameBrand(store, product);
            return new StoreProductSetting(setting, product);
        }).toList();
    }

    @Transactional
    public StoreProductSetting updateSalesSetting(Long storeId, Long productId, BigDecimal salePrice,
                                                   ProductSaleStatus saleStatus, boolean exposed,
                                                   boolean inventoryManaged) {
        CommerceStore store = requireStore(storeId);
        Product product = requireProduct(productId);
        requireSameBrand(store, product);
        StoreProduct setting = storeProducts.findByStoreIdAndProductId(storeId, productId)
                .orElseGet(() -> StoreProduct.builder().storeId(storeId).productId(productId)
                        .salePrice(product.getSalePrice()).saleStatus(product.getSaleStatus()).exposed(true)
                        .inventoryManaged(product.isInventoryManaged()).build());
        setting.update(salePrice, saleStatus, exposed, inventoryManaged);
        return new StoreProductSetting(storeProducts.save(setting), product);
    }

    private void requireBrand(Long id) {
        if (!brands.existsById(id)) throw new IllegalArgumentException("브랜드를 찾을 수 없습니다.");
    }
    private CommerceStore requireStore(Long id) {
        return stores.findById(id).orElseThrow(() -> new IllegalArgumentException("매장을 찾을 수 없습니다."));
    }
    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }
    private void requireSameBrand(CommerceStore store, Product product) {
        if (store.getBrandId() == null || !store.getBrandId().equals(product.getBrandId())) {
            throw new IllegalArgumentException("해당 매장에서 관리할 수 없는 상품입니다.");
        }
    }

    public record StoreProductSetting(StoreProduct setting, Product product) {}
}
