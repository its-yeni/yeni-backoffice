package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.commerce.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Order(100)
public class BrandStoreMigrationInitializer implements CommandLineRunner {
    private final CommerceBrandRepository brands;private final CommerceStoreRepository stores;private final ProductRepository products;private final ProductCategoryRepository categories;private final StoreProductRepository storeProducts;private final ProductVariantRepository variants;private final StoreVariantInventoryRepository inventories;
    public BrandStoreMigrationInitializer(CommerceBrandRepository brands,CommerceStoreRepository stores,ProductRepository products,ProductCategoryRepository categories,StoreProductRepository storeProducts,ProductVariantRepository variants,StoreVariantInventoryRepository inventories){this.brands=brands;this.stores=stores;this.products=products;this.categories=categories;this.storeProducts=storeProducts;this.variants=variants;this.inventories=inventories;}
    @Override @Transactional public void run(String... args){
        CommerceBrand food=brand("YENI-TABLE","Yeni Table",StoreBusinessType.FOOD_SERVICE);CommerceBrand shop=brand("YENI-SELECT","Yeni Select",StoreBusinessType.ONLINE_RETAIL);
        stores.findAll().forEach(store->{CommerceBrand brand=store.getStoreCode().startsWith("YENI-FOOD")?food:shop;store.assignBrand(brand.getId());});
        categories.findAll().forEach(category->category.assignBrand("YENI-FOOD-01".equals(category.getStoreCode())?food.getId():shop.getId()));
        products.findAll().forEach(product->{CommerceBrand brand="YENI-FOOD-01".equals(product.getStoreCode())?food:shop;product.assignBrand(brand.getId());stores.findByStoreCode(product.getStoreCode()).ifPresent(store->{StoreProduct setting=storeProducts.findByStoreIdAndProductId(store.getId(),product.getId()).orElseGet(()->storeProducts.save(StoreProduct.builder().storeId(store.getId()).productId(product.getId()).salePrice(product.getSalePrice()).saleStatus(product.getSaleStatus()).exposed(product.getSaleStatus().name().equals("ON_SALE")).inventoryManaged(product.isInventoryManaged()).build()));variants.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).forEach(variant->{if(inventories.findByStoreIdAndVariantId(store.getId(),variant.getId()).isEmpty())inventories.save(StoreVariantInventory.builder().storeId(store.getId()).variantId(variant.getId()).stockQuantity(variant.getStockQuantity()).reservedQuantity(0).averageUnitCost(java.math.BigDecimal.ZERO).saleEnabled(true).build());});});});
    }
    private CommerceBrand brand(String code,String name,StoreBusinessType type){return brands.findByBrandCode(code).orElseGet(()->brands.save(CommerceBrand.builder().brandCode(code).brandName(name).businessType(type).active(true).build()));}
}
