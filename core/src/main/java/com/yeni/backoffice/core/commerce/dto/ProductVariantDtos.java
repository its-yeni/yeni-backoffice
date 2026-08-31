package com.yeni.backoffice.core.commerce.dto;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class ProductVariantDtos{private ProductVariantDtos(){}
 public record VariantUpdateRequest(@NotBlank String sku,String barcode,@NotNull @PositiveOrZero BigDecimal additionalPrice,@PositiveOrZero int stockQuantity,@NotBlank String saleStatus){}
 public record VariantReceiveRequest(@jakarta.validation.constraints.Positive int quantity,String reason,Long storeId,@PositiveOrZero BigDecimal unitCost,String lotNo,java.time.LocalDate manufacturedDate,java.time.LocalDate expirationDate,String memo){public VariantReceiveRequest(int quantity,String reason){this(quantity,reason,null,BigDecimal.ZERO,null,null,null,null);}}
 public record VariantAdjustRequest(@jakarta.validation.constraints.NotNull Integer delta,@NotBlank String reason){}
 public record VariantResponse(Long id,Long productId,String sku,String barcode,String combinationKey,String optionSummary,BigDecimal additionalPrice,int stockQuantity,int reservedQuantity,int availableQuantity,String saleStatus,int sortOrder){public static VariantResponse from(ProductVariant v){return new VariantResponse(v.getId(),v.getProductId(),v.getSku(),v.getBarcode(),v.getCombinationKey(),v.getOptionSummary(),v.getAdditionalPrice(),v.getStockQuantity(),v.getReservedQuantity(),v.getAvailableQuantity(),v.getSaleStatus().name(),v.getSortOrder());}}
 public record VariantInventoryResponse(Long variantId,Long productId,String productName,String productCode,String category,String sku,String barcode,String optionSummary,int stockQuantity,int reservedQuantity,int availableQuantity,int safetyStock,String saleStatus,java.time.LocalDateTime updatedAt){}
}
