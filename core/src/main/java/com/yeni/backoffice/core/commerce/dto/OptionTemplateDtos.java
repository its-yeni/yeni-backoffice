package com.yeni.backoffice.core.commerce.dto;import com.yeni.backoffice.core.commerce.entity.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.util.*;
public final class OptionTemplateDtos{private OptionTemplateDtos(){}
 // values는 템플릿 생성 시에만 사용한다(그룹+값을 한 트랜잭션으로 같이 저장). 수정(update)에서는 무시된다.
 public record TemplateGroupRequest(@NotBlank String templateName,String customerDisplayName,@NotBlank String selectionType,boolean requiredOption,@PositiveOrZero int minSelection,@Positive int maxSelection,@Valid List<TemplateValueRequest> values){
  public TemplateGroupRequest{if(values==null)values=List.of();}
 }
 public record TemplateValueRequest(@NotBlank String valueName,@NotNull @PositiveOrZero BigDecimal defaultAdditionalPrice){}
 public record TemplateValueResponse(Long id,String valueName,BigDecimal defaultAdditionalPrice,int sortOrder){public static TemplateValueResponse from(OptionValueTemplate v){return new TemplateValueResponse(v.getId(),v.getValueName(),v.getDefaultAdditionalPrice(),v.getSortOrder());}}
 public record TemplateGroupResponse(Long id,String templateName,String customerDisplayName,String selectionType,boolean requiredOption,int minSelection,int maxSelection,int sortOrder,List<TemplateValueResponse> values){public static TemplateGroupResponse from(OptionGroupTemplate g,List<OptionValueTemplate> v){return new TemplateGroupResponse(g.getId(),g.getTemplateName(),g.displayName(),g.getSelectionType().name(),g.isRequiredOption(),g.getMinSelection(),g.getMaxSelection(),g.getSortOrder(),v.stream().map(TemplateValueResponse::from).toList());}}
}
