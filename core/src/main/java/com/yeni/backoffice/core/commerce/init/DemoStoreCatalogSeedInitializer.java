package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.entity.ProductCategory;
import com.yeni.backoffice.core.commerce.entity.ProductOptionGroup;
import com.yeni.backoffice.core.commerce.entity.ProductOptionValue;
import com.yeni.backoffice.core.commerce.entity.StoreProduct;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.repository.ProductCategoryRepository;
import com.yeni.backoffice.core.commerce.repository.ProductOptionGroupRepository;
import com.yeni.backoffice.core.commerce.repository.ProductOptionValueRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.commerce.repository.StoreProductRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 공개 데모의 모든 매장에서 동일 브랜드의 10개 상품 카탈로그를 조회할 수 있게 한다. */
@Component
@Profile("fly | test | demo")
@Order(120)
public class DemoStoreCatalogSeedInitializer implements CommandLineRunner {

    private final CommerceStoreRepository stores;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final ProductOptionGroupRepository optionGroups;
    private final ProductOptionValueRepository optionValues;
    private final StoreProductRepository storeProducts;
    private final ProductVariantRepository variants;

    public DemoStoreCatalogSeedInitializer(CommerceStoreRepository stores,
                                           ProductRepository products,
                                           ProductCategoryRepository categories,
                                           ProductOptionGroupRepository optionGroups,
                                           ProductOptionValueRepository optionValues,
                                           StoreProductRepository storeProducts,
                                           ProductVariantRepository variants) {
        this.stores = stores;
        this.products = products;
        this.categories = categories;
        this.optionGroups = optionGroups;
        this.optionValues = optionValues;
        this.storeProducts = storeProducts;
        this.variants = variants;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureBaseCatalog("YENI-FOOD-01", true);
        ensureBaseCatalog("YENI-SHOP-01", false);
        for (CommerceStore store : stores.findAllByOrderByIdAsc()) {
            String sourceStoreCode = store.getBusinessType() == StoreBusinessType.FOOD_SERVICE
                    ? "YENI-FOOD-01" : "YENI-SHOP-01";
            if (sourceStoreCode.equals(store.getStoreCode())) {
                ensureStoreSettings(store, productsByStore(sourceStoreCode));
                continue;
            }
            copyCategories(sourceStoreCode, store);
            copyProductsAndOptions(sourceStoreCode, store);
        }
    }

    private void ensureBaseCatalog(String storeCode, boolean food) {
        CommerceStore store = stores.findByStoreCode(storeCode).orElseThrow();
        String[] categoryNames = food
                ? new String[]{"피자", "사이드", "음료", "세트", "파스타", "샐러드", "디저트", "커피", "브런치", "키즈"}
                : new String[]{"기본", "의류", "잡화", "리빙", "문구", "디지털", "홈리빙", "오피스", "선물", "시즌"};
        String[][] catalog = food
                ? new String[][]{{"FOOD-PIZZA-001","Yeni 시그니처 피자","피자","23900","100"},{"FOOD-SIDE-001","갈릭 치즈볼","사이드","6900","80"},{"FOOD-DRINK-001","콜라 500ml","음료","2500","200"},{"FOOD-SET-001","시그니처 피자 세트","세트","31900","60"},{"FOOD-PASTA-001","트러플 크림 파스타","파스타","16900","70"},{"FOOD-SALAD-001","그릴 치킨 샐러드","샐러드","11900","55"},{"FOOD-DESSERT-001","바스크 치즈 케이크","디저트","6500","45"},{"FOOD-COFFEE-001","콜드브루 라떼","커피","5200","120"},{"FOOD-BRUNCH-001","에그 베네딕트 플레이트","브런치","14900","35"},{"FOOD-KIDS-001","키즈 미니 피자 세트","키즈","9900","40"},
                    {"FOOD-PIZZA-002","페퍼로니 피자","피자","21900","90"},{"FOOD-PIZZA-003","고르곤졸라 피자","피자","25900","70"},{"FOOD-SIDE-002","포테이토 웨지","사이드","4900","150"},{"FOOD-SIDE-003","치킨 텐더 5조각","사이드","8900","100"},{"FOOD-DRINK-002","스파클링 워터","음료","3000","120"},{"FOOD-SET-002","커플 콤보 세트","세트","27900","45"},{"FOOD-PASTA-002","토마토 라구 파스타","파스타","15900","60"},{"FOOD-DESSERT-002","클래식 티라미수","디저트","6900","40"},{"FOOD-COFFEE-002","아메리카노","커피","4000","200"},{"FOOD-BRUNCH-002","리코타 팬케이크","브런치","13900","30"}}
                : new String[][]{{"YENI-TEE-001","Yeni 시그니처 티셔츠","의류","29000","30"},{"YENI-BAG-001","Yeni 데일리 토트백","잡화","39000","20"},{"YENI-MUG-001","Yeni 오피스 머그","리빙","15000","50"},{"YENI-NOTE-001","Yeni 위클리 플래너","문구","12000","85"},{"YENI-PEN-001","Yeni 젤 펜 세트","문구","8900","120"},{"YENI-STAND-001","알루미늄 노트북 스탠드","디지털","42000","18"},{"YENI-LAMP-001","Yeni 무드 테이블 램프","홈리빙","59000","14"},{"YENI-POUCH-001","Yeni 데일리 파우치","잡화","19000","42"},{"YENI-GIFT-001","Yeni 웰컴 기프트 세트","선물","35000","26"},{"YENI-KEYRING-001","Yeni 로고 키링","리빙","7900","95"},
                    {"YENI-HOODIE-001","Yeni 베이직 후드","의류","49000","40"},{"YENI-SOCKS-001","Yeni 데일리 삭스 3P","의류","9900","150"},{"YENI-CARD-001","Yeni 카드 지갑","잡화","22000","35"},{"YENI-BOTTLE-001","Yeni 보온 텀블러","리빙","24000","60"},{"YENI-COASTER-001","Yeni 우드 코스터 세트","홈리빙","12000","70"},{"YENI-CABLE-001","Yeni 편조 충전 케이블","디지털","11000","90"},{"YENI-DESKMAT-001","Yeni 데스크 매트","오피스","26000","30"},{"YENI-STICKY-001","Yeni 인덱스 점착 메모","문구","4500","200"},{"YENI-CANDLE-001","Yeni 시그니처 캔들","시즌","28000","22"},{"YENI-ECOBAG-001","Yeni 캔버스 에코백","잡화","16000","55"}};
        for (int i = 0; i < categoryNames.length; i++) {
            String name = categoryNames[i];
            if (categories.findByStoreCodeAndCategoryName(storeCode, name).isEmpty()) {
                categories.save(ProductCategory.builder().storeCode(storeCode).brandId(store.getBrandId()).categoryName(name).sortOrder(i + 1).exposed(true).build());
            }
        }
        for (String[] item : catalog) {
            Product product = products.findByStoreCodeAndProductCode(storeCode, item[0]).orElseGet(() -> products.save(Product.builder()
                    .storeCode(storeCode).brandId(store.getBrandId()).productCode(item[0]).productName(item[1]).category(item[2])
                    .salePrice(new java.math.BigDecimal(item[3])).stockQuantity(Integer.parseInt(item[4])).inventoryManaged(true)
                    .saleStatus(com.yeni.backoffice.core.commerce.enums.ProductSaleStatus.ON_SALE).build()));
            ensureDemoOption(product, food);
            ensureBaseVariant(product);
        }
    }

    private void ensureDemoOption(Product product, boolean food) {
        if (!optionGroups.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).isEmpty()) return;
        ProductOptionGroup group = optionGroups.save(ProductOptionGroup.builder().productId(product.getId())
                .groupName(food ? "선택 옵션" : "상품 옵션").selectionType(com.yeni.backoffice.core.commerce.enums.OptionSelectionType.SINGLE)
                .requiredOption(false).minSelection(0).maxSelection(1).sortOrder(1).exposed(true).build());
        optionValues.save(ProductOptionValue.builder().optionGroupId(group.getId()).valueName("기본")
                .additionalPrice(java.math.BigDecimal.ZERO).inventoryManaged(false).stockQuantity(0)
                .saleStatus(com.yeni.backoffice.core.commerce.enums.ProductSaleStatus.ON_SALE).sortOrder(1).build());
    }

    private void copyCategories(String sourceStoreCode, CommerceStore targetStore) {
        List<ProductCategory> source = categories.findByStoreCodeOrderBySortOrderAscIdAsc(sourceStoreCode);
        for (ProductCategory category : source) {
            if (categories.findByStoreCodeAndCategoryName(targetStore.getStoreCode(), category.getCategoryName()).isPresent()) {
                continue;
            }
            categories.save(ProductCategory.builder()
                    .storeCode(targetStore.getStoreCode())
                    .brandId(targetStore.getBrandId())
                    .categoryName(category.getCategoryName())
                    .sortOrder(category.getSortOrder())
                    .exposed(category.isExposed())
                    .build());
        }
    }

    private void copyProductsAndOptions(String sourceStoreCode, CommerceStore targetStore) {
        // 매장마다 브랜드 전체 카탈로그 중 일부만 취급한다 — 채널별로 상품 목록이 실제로 달라 보이게.
        List<Product> full = productsByStore(sourceStoreCode);
        List<Product> subset = curatedSubset(full, targetStore.getStoreCode());
        for (Product source : subset) {
            Product target = products.findByStoreCodeAndProductCode(targetStore.getStoreCode(), source.getProductCode())
                    .orElseGet(() -> products.save(Product.builder()
                            .storeCode(targetStore.getStoreCode())
                            .brandId(targetStore.getBrandId())
                            .productCode(source.getProductCode())
                            .productName(source.getProductName())
                            .category(source.getCategory())
                            .imageUrl(source.getImageUrl())
                            .salePrice(source.getSalePrice())
                            .stockQuantity(source.getStockQuantity())
                            .inventoryManaged(source.isInventoryManaged())
                            .saleStatus(source.getSaleStatus())
                            .build()));
            copyOptions(source, target);
            ensureBaseVariant(target);
            ensureStoreSetting(targetStore, target);
        }
    }

    private void copyOptions(Product source, Product target) {
        if (!optionGroups.findByProductIdOrderBySortOrderAscIdAsc(target.getId()).isEmpty()) {
            return;
        }
        for (ProductOptionGroup sourceGroup : optionGroups.findByProductIdOrderBySortOrderAscIdAsc(source.getId())) {
            ProductOptionGroup targetGroup = optionGroups.save(ProductOptionGroup.builder()
                    .productId(target.getId())
                    .groupName(sourceGroup.getGroupName())
                    .selectionType(sourceGroup.getSelectionType())
                    .requiredOption(sourceGroup.isRequiredOption())
                    .minSelection(sourceGroup.getMinSelection())
                    .maxSelection(sourceGroup.getMaxSelection())
                    .sortOrder(sourceGroup.getSortOrder())
                    .exposed(sourceGroup.isExposed())
                    .build());
            for (ProductOptionValue sourceValue : optionValues.findByOptionGroupIdOrderBySortOrderAscIdAsc(sourceGroup.getId())) {
                optionValues.save(ProductOptionValue.builder()
                        .optionGroupId(targetGroup.getId())
                        .valueName(sourceValue.getValueName())
                        .additionalPrice(sourceValue.getAdditionalPrice())
                        .inventoryManaged(sourceValue.isInventoryManaged())
                        .stockQuantity(sourceValue.getStockQuantity())
                        .saleStatus(sourceValue.getSaleStatus())
                        .sortOrder(sourceValue.getSortOrder())
                        .build());
            }
        }
    }

    private void ensureStoreSettings(CommerceStore store, List<Product> storeCatalog) {
        storeCatalog.forEach(product -> ensureStoreSetting(store, product));
    }

    private void ensureStoreSetting(CommerceStore store, Product product) {
        if (storeProducts.findByStoreIdAndProductId(store.getId(), product.getId()).isPresent()) {
            return;
        }
        storeProducts.save(StoreProduct.builder()
                .storeId(store.getId())
                .productId(product.getId())
                .salePrice(product.getSalePrice())
                .saleStatus(product.getSaleStatus())
                .exposed(true)
                .inventoryManaged(product.isInventoryManaged())
                .build());
    }

    private void ensureBaseVariant(Product product) {
        if (!variants.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).isEmpty()) return;
        variants.save(ProductVariant.builder()
                .productId(product.getId())
                .sku(product.getProductCode() + "-" + product.getId() + "-BASE")
                .combinationKey("BASE")
                .optionSummary("기본")
                .additionalPrice(java.math.BigDecimal.ZERO)
                .stockQuantity(product.getStockQuantity())
                .reservedQuantity(0)
                .saleStatus(product.getSaleStatus())
                .sortOrder(1)
                .build());
    }

    private List<Product> productsByStore(String storeCode) {
        return products.findAll().stream().filter(product -> storeCode.equals(product.getStoreCode())).toList();
    }

    /** 대상 매장 코드로 시드한 결정적 부분집합(전체의 60~80%). 채널마다 취급 상품이 다르게 보이도록 한다. */
    private List<Product> curatedSubset(List<Product> full, String storeCode) {
        if (full.size() <= 8) return full;
        List<Product> sorted = new java.util.ArrayList<>(full);
        sorted.sort(java.util.Comparator.comparing(Product::getProductCode));
        java.util.Random rnd = new java.util.Random(storeCode.hashCode());
        java.util.Collections.shuffle(sorted, rnd);
        int take = Math.max(8, (int) Math.round(full.size() * (0.6 + rnd.nextDouble() * 0.2)));
        return new java.util.ArrayList<>(sorted.subList(0, Math.min(take, sorted.size())));
    }
}
