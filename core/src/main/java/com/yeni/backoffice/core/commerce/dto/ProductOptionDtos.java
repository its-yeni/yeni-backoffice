package com.yeni.backoffice.core.commerce.dto;import com.yeni.backoffice.core.commerce.entity.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.util.*;
public final class ProductOptionDtos{private ProductOptionDtos(){}
 // values는 그룹 생성 시에만 사용한다(그룹+값을 한 트랜잭션으로 같이 저장). 수정(update)에서는 무시된다.
 public record GroupRequest(@NotBlank String groupName,@NotBlank String selectionType,boolean requiredOption,@PositiveOrZero int minSelection,@Positive int maxSelection,boolean exposed,@Valid List<ValueRequest> values){
  public GroupRequest{if(values==null)values=List.of();}
 }
 public record ValueRequest(@NotBlank String valueName,@NotNull @PositiveOrZero BigDecimal additionalPrice,boolean inventoryManaged,@PositiveOrZero int stockQuantity,@NotBlank String saleStatus){}
 public record ValueResponse(Long id,String valueName,BigDecimal additionalPrice,boolean inventoryManaged,int stockQuantity,String saleStatus,int sortOrder){public static ValueResponse from(ProductOptionValue v){return new ValueResponse(v.getId(),v.getValueName(),v.getAdditionalPrice(),v.isInventoryManaged(),v.getStockQuantity(),v.getSaleStatus().name(),v.getSortOrder());}}
 public record GroupResponse(Long id,Long productId,String groupName,String selectionType,boolean requiredOption,int minSelection,int maxSelection,int sortOrder,boolean exposed,List<ValueResponse> values){public static GroupResponse from(ProductOptionGroup g,List<ProductOptionValue> v){return new GroupResponse(g.getId(),g.getProductId(),g.getGroupName(),g.getSelectionType().name(),g.isRequiredOption(),g.getMinSelection(),g.getMaxSelection(),g.getSortOrder(),g.isExposed(),v.stream().map(ValueResponse::from).toList());}}
 public record AddonGroupRequest(@NotBlank String groupName,@PositiveOrZero int minQuantity,@Positive int maxQuantity,boolean exposed){}
 public record AddonItemRequest(@NotNull Long addonProductId,@Positive int maxQuantity){}
 public record AddonItemResponse(Long id,Long productId,String productName,BigDecimal salePrice,int maxQuantity,int sortOrder){}
 public record AddonGroupResponse(Long id,Long productId,String groupName,int minQuantity,int maxQuantity,int sortOrder,boolean exposed,List<AddonItemResponse> items){}
 public record ReorderRequest(@NotEmpty List<@NotNull Long> ids){}
}
