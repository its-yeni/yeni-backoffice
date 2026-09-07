package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.pos.entity.PosTerminal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.yeni.backoffice.core.common.exception.*;

@Service
public class PosInventoryLookupService {
    private final CommerceStoreRepository stores;private final ProductRepository products;private final ProductVariantRepository variants;private final StoreVariantInventoryRepository inventories;
    public PosInventoryLookupService(CommerceStoreRepository stores,ProductRepository products,ProductVariantRepository variants,StoreVariantInventoryRepository inventories){this.stores=stores;this.products=products;this.variants=variants;this.inventories=inventories;}

    @Transactional(readOnly=true)
    public List<InventoryItem> search(PosTerminal terminal,String keyword,Integer requestedLimit){
        String storeCode=stores.findById(terminal.getStoreId()).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"매장을 찾을 수 없습니다.")).getStoreCode();int limit=Math.min(Math.max(requestedLimit==null?30:requestedLimit,1),100);
        if(StringUtils.hasText(keyword)){String q=keyword.trim();ProductVariant exact=variants.findByBarcode(q).or(()->variants.findBySku(q)).orElse(null);if(exact!=null){Product product=products.findById(exact.getProductId()).orElse(null);if(product!=null&&storeCode.equals(product.getStoreCode()))return List.of(toItem(terminal.getStoreId(),product,exact));}}
        List<Product> found=products.search(StringUtils.hasText(keyword)?keyword.trim():null,null,storeCode,PageRequest.of(0,limit)).getContent();
        List<ProductVariant> variantRows=found.isEmpty()?List.of():variants.findByProductIdInOrderByProductIdAscSortOrderAscIdAsc(found.stream().map(Product::getId).toList());
        Map<Long,List<ProductVariant>> byProduct=variantRows.stream().collect(Collectors.groupingBy(ProductVariant::getProductId));
        List<Long> variantIds=variantRows.stream().map(ProductVariant::getId).toList();
        Map<Long,StoreVariantInventory> byVariant=(variantIds.isEmpty()?List.<StoreVariantInventory>of():inventories.findByStoreIdAndVariantIdIn(terminal.getStoreId(),variantIds)).stream().collect(Collectors.toMap(StoreVariantInventory::getVariantId,Function.identity()));
        return found.stream().flatMap(product->{List<ProductVariant> rows=byProduct.getOrDefault(product.getId(),List.of());return rows.isEmpty()?java.util.stream.Stream.of(toItem(product,null,null)):rows.stream().map(variant->toItem(product,variant,byVariant.get(variant.getId())));}).limit(limit).toList();
    }
    private InventoryItem toItem(Long storeId,Product product,ProductVariant variant){return toItem(product,variant,variant==null?null:inventories.findByStoreIdAndVariantId(storeId,variant.getId()).orElse(null));}
    private InventoryItem toItem(Product product,ProductVariant variant,StoreVariantInventory inventory){
        if(variant==null)return new InventoryItem(product.getId(),null,product.getProductCode(),null,product.getProductName(),product.getSalePrice(),product.getStockQuantity(),0,product.getStockQuantity(),product.getUpdatedAt());
        int stock=inventory==null?variant.getStockQuantity():inventory.getStockQuantity();int reserved=inventory==null?variant.getReservedQuantity():inventory.getReservedQuantity();
        return new InventoryItem(product.getId(),variant.getId(),variant.getSku(),variant.getBarcode(),product.getProductName()+(StringUtils.hasText(variant.getOptionSummary())?" · "+variant.getOptionSummary():""),product.getSalePrice().add(variant.getAdditionalPrice()),stock,reserved,Math.max(0,stock-reserved),inventory==null?variant.getUpdatedAt():inventory.getUpdatedAt());
    }
    public record InventoryItem(Long productId,Long variantId,String sku,String barcode,String displayName,BigDecimal salePrice,int stockQuantity,int reservedQuantity,int availableQuantity,LocalDateTime lastSynchronizedSourceAt){}
}
