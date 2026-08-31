package com.yeni.backoffice.core.commerce.dto;
import com.yeni.backoffice.core.commerce.entity.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
public final class ProductCategoryDtos {
    private ProductCategoryDtos(){}
    public record CategoryRequest(@NotBlank String categoryName,Boolean exposed,String storeCode){public CategoryRequest(String categoryName,Boolean exposed){this(categoryName,exposed,null);}}
    public record CategoryResponse(Long id,String categoryName,int sortOrder,boolean exposed,String storeCode,LocalDateTime updatedAt){
        public static CategoryResponse from(ProductCategory c){return new CategoryResponse(c.getId(),c.getCategoryName(),c.getSortOrder(),c.isExposed(),c.getStoreCode(),c.getUpdatedAt());}
    }
}
