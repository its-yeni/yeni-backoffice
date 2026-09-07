package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PosCatalogSyncService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final CommerceStoreRepository stores;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductOptionGroupRepository optionGroups;
    private final ProductOptionValueRepository optionValues;
    private final StoreVariantInventoryRepository inventories;

    public PosCatalogSyncService(CommerceStoreRepository stores, ProductRepository products,
            ProductVariantRepository variants, ProductOptionGroupRepository optionGroups,
            ProductOptionValueRepository optionValues, StoreVariantInventoryRepository inventories) {
        this.stores=stores; this.products=products; this.variants=variants; this.optionGroups=optionGroups;
        this.optionValues=optionValues; this.inventories=inventories;
    }

    @Transactional(readOnly=true)
    public CatalogPage pull(Long storeId, Long afterProductId, Integer requestedLimit) {
        CommerceStore store=stores.findById(storeId)
                .orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"매장을 찾을 수 없습니다."));
        int limit=Math.min(Math.max(requestedLimit==null?DEFAULT_LIMIT:requestedLimit,1),MAX_LIMIT);
        List<Product> page=products.findByStoreCodeAndIdGreaterThanOrderByIdAsc(store.getStoreCode(),
                afterProductId==null?0L:Math.max(0L,afterProductId),PageRequest.of(0,limit+1));
        boolean hasMore=page.size()>limit;
        List<Product> selected=hasMore?page.subList(0,limit):page;
        List<Long> productIds=selected.stream().map(Product::getId).toList();
        Map<Long,List<ProductVariant>> variantsByProduct=productIds.isEmpty()?Map.of():variants
                .findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId,LinkedHashMap::new,Collectors.toList()));
        List<ProductOptionGroup> groups=productIds.isEmpty()?List.of():optionGroups
                .findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(productIds);
        List<Long> groupIds=groups.stream().map(ProductOptionGroup::getId).toList();
        Map<Long,List<ProductOptionValue>> valuesByGroup=groupIds.isEmpty()?Map.of():optionValues
                .findByOptionGroupIdInOrderByOptionGroupIdAscSortOrderAscIdAsc(groupIds).stream()
                .collect(Collectors.groupingBy(ProductOptionValue::getOptionGroupId,LinkedHashMap::new,Collectors.toList()));
        Map<Long,List<ProductOptionGroup>> groupsByProduct=groups.stream()
                .collect(Collectors.groupingBy(ProductOptionGroup::getProductId,LinkedHashMap::new,Collectors.toList()));
        List<Long> variantIds=variantsByProduct.values().stream().flatMap(Collection::stream).map(ProductVariant::getId).toList();
        Map<Long,StoreVariantInventory> inventoryByVariant=(variantIds.isEmpty()?List.<StoreVariantInventory>of():
                inventories.findByStoreIdAndVariantIdIn(storeId,variantIds)).stream()
                .collect(Collectors.toMap(StoreVariantInventory::getVariantId,Function.identity()));
        List<CatalogProduct> data=selected.stream().map(product->toProduct(product,
                variantsByProduct.getOrDefault(product.getId(),List.of()),
                groupsByProduct.getOrDefault(product.getId(),List.of()),valuesByGroup,inventoryByVariant)).toList();
        Long nextCursor=hasMore?selected.get(selected.size()-1).getId():null;
        return new CatalogPage("FULL",LocalDateTime.now(),nextCursor,hasMore,data);
    }

    private CatalogProduct toProduct(Product product,List<ProductVariant> variants,List<ProductOptionGroup> groups,
            Map<Long,List<ProductOptionValue>> valuesByGroup,Map<Long,StoreVariantInventory> inventoryByVariant){
        return new CatalogProduct(product.getId(),product.getProductCode(),product.getProductName(),product.getCategory(),
                product.getImageUrl(),product.getSalePrice(),product.isInventoryManaged(),product.getSaleStatus().name(),
                product.getUpdatedAt(),groups.stream().map(group->new OptionGroup(group.getId(),group.getGroupName(),
                        group.getSelectionType().name(),group.isRequiredOption(),group.getMinSelection(),group.getMaxSelection(),
                        group.getSortOrder(),group.isExposed(),valuesByGroup.getOrDefault(group.getId(),List.of()).stream()
                                .map(value->new OptionValue(value.getId(),value.getValueName(),value.getAdditionalPrice(),
                                        value.getSaleStatus().name(),value.getSortOrder())).toList())).toList(),
                variants.stream().map(variant->toVariant(variant,inventoryByVariant.get(variant.getId()))).toList());
    }

    private Variant toVariant(ProductVariant variant,StoreVariantInventory inventory){
        int stock=inventory==null?variant.getStockQuantity():inventory.getStockQuantity();
        int reserved=inventory==null?variant.getReservedQuantity():inventory.getReservedQuantity();
        boolean enabled=inventory==null||inventory.isSaleEnabled();
        return new Variant(variant.getId(),variant.getSku(),variant.getBarcode(),variant.getCombinationKey(),
                variant.getOptionSummary(),variant.getAdditionalPrice(),stock,reserved,Math.max(0,stock-reserved),
                enabled,variant.getSaleStatus().name(),variant.getUpdatedAt());
    }

    public record CatalogPage(String mode,LocalDateTime generatedAt,Long nextCursor,boolean hasMore,List<CatalogProduct> items){}
    public record CatalogProduct(Long productId,String productCode,String productName,String category,String imageUrl,
            BigDecimal salePrice,boolean inventoryManaged,String saleStatus,LocalDateTime updatedAt,
            List<OptionGroup> optionGroups,List<Variant> variants){}
    public record OptionGroup(Long groupId,String groupName,String selectionType,boolean required,int minSelection,
            int maxSelection,int sortOrder,boolean exposed,List<OptionValue> values){}
    public record OptionValue(Long valueId,String valueName,BigDecimal additionalPrice,String saleStatus,int sortOrder){}
    public record Variant(Long variantId,String sku,String barcode,String combinationKey,String optionSummary,
            BigDecimal additionalPrice,int stockQuantity,int reservedQuantity,int availableQuantity,
            boolean saleEnabled,String saleStatus,LocalDateTime updatedAt){}
}
