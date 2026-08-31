package com.yeni.backoffice.core.commerce.dto;
import com.yeni.backoffice.core.commerce.entity.Product;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public final class ProductDtos {
    private ProductDtos(){}
    public record ProductSaveRequest(String productCode,@NotBlank String productName,String category,
        @NotNull @Positive BigDecimal salePrice,@PositiveOrZero int stockQuantity,@NotBlank String saleStatus,String imageUrl,Boolean inventoryManaged,String storeCode,List<Long> optionTemplateIds){
        public ProductSaveRequest(String code,String name,String category,BigDecimal price,int stock,String status){this(code,name,category,price,stock,status,null,true,null,List.of());}
    }
    public record ProductStatusRequest(@NotBlank String saleStatus){}
    public record ProductResponse(Long id,String productCode,String productName,String category,String imageUrl,
        BigDecimal salePrice,int stockQuantity,boolean inventoryManaged,String saleStatus,String storeCode,LocalDateTime createdAt,LocalDateTime updatedAt){
        public static ProductResponse from(Product p){return new ProductResponse(p.getId(),p.getProductCode(),p.getProductName(),p.getCategory(),p.getImageUrl(),p.getSalePrice(),p.getStockQuantity(),p.isInventoryManaged(),p.getSaleStatus().name(),p.getStoreCode(),p.getCreatedAt(),p.getUpdatedAt());}
    }
    public record ProductPageResponse(List<ProductResponse> items,long totalCount,int page,int size){}
}
