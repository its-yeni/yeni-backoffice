package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.ProductOptionDtos.*;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.*;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ProductOptionService {
    private final ProductRepository products; private final ProductOptionGroupRepository groups;
    private final ProductOptionValueRepository values; private final ProductAddonGroupRepository addonGroups;
    private final ProductAddonItemRepository addonItems; private final OptionTemplateService templates;
    public ProductOptionService(ProductRepository p,ProductOptionGroupRepository g,ProductOptionValueRepository v,ProductAddonGroupRepository ag,ProductAddonItemRepository ai,OptionTemplateService t){products=p;groups=g;values=v;addonGroups=ag;addonItems=ai;templates=t;}
    // 템플릿 값을 상품 전용 옵션 그룹/값으로 복사한다(스냅샷). 재고는 상품마다 다르므로 항상
    // "재고 미사용 · 판매중" 상태로 시작하고, 관리자가 상품에 맞게 재고/추가금액을 다시 조정한다.
    @Transactional public GroupResponse applyTemplate(Long productId,Long templateGroupId){
        product(productId);
        OptionGroupTemplate t=templates.group(templateGroupId);
        List<OptionValueTemplate> templateValues=templates.valuesOf(templateGroupId);
        int order=groups.findByProductIdOrderBySortOrderAscIdAsc(productId).size()+1;
        ProductOptionGroup g=groups.save(ProductOptionGroup.builder().productId(productId).groupName(t.displayName()).selectionType(t.getSelectionType()).requiredOption(t.isRequiredOption()).minSelection(t.getMinSelection()).maxSelection(t.getMaxSelection()).sortOrder(order).exposed(true).build());
        List<ProductOptionValue> created=new ArrayList<>();
        int valueOrder=1;
        for(OptionValueTemplate tv:templateValues)created.add(values.save(ProductOptionValue.builder().optionGroupId(g.getId()).valueName(tv.getValueName()).additionalPrice(tv.getDefaultAdditionalPrice()).inventoryManaged(false).stockQuantity(0).saleStatus(ProductSaleStatus.ON_SALE).sortOrder(valueOrder++).build()));
        return GroupResponse.from(g,created);
    }
    @Transactional(readOnly=true) public List<GroupResponse> groups(Long productId){product(productId);return groups.findByProductIdOrderBySortOrderAscIdAsc(productId).stream().map(g->GroupResponse.from(g,values.findByOptionGroupIdOrderBySortOrderAscIdAsc(g.getId()))).toList();}
    // 그룹과 초기 옵션값들을 한 트랜잭션으로 같이 저장한다. 값 저장 중 하나라도 실패하면 그룹 생성도 롤백된다.
    @Transactional public GroupResponse createGroup(Long productId,GroupRequest r){
        product(productId);validate(r.minSelection(),r.maxSelection());
        int order=groups.findByProductIdOrderBySortOrderAscIdAsc(productId).size()+1;
        ProductOptionGroup g=groups.save(ProductOptionGroup.builder().productId(productId).groupName(r.groupName().trim()).selectionType(type(r.selectionType())).requiredOption(r.requiredOption()).minSelection(r.minSelection()).maxSelection(r.maxSelection()).sortOrder(order).exposed(r.exposed()).build());
        List<ValueResponse> created=new ArrayList<>();
        int valueOrder=1;
        for(ValueRequest vr:r.values()){
            if(vr.valueName()==null||vr.valueName().isBlank())continue;
            created.add(ValueResponse.from(values.save(ProductOptionValue.builder().optionGroupId(g.getId()).valueName(vr.valueName().trim()).additionalPrice(vr.additionalPrice()).inventoryManaged(vr.inventoryManaged()).stockQuantity(vr.stockQuantity()).saleStatus(status(vr.saleStatus())).sortOrder(valueOrder++).build())));
        }
        return new GroupResponse(g.getId(),g.getProductId(),g.getGroupName(),g.getSelectionType().name(),g.isRequiredOption(),g.getMinSelection(),g.getMaxSelection(),g.getSortOrder(),g.isExposed(),created);
    }
    @Transactional public GroupResponse updateGroup(Long id,GroupRequest r){validate(r.minSelection(),r.maxSelection());ProductOptionGroup g=group(id);g.update(r.groupName().trim(),type(r.selectionType()),r.requiredOption(),r.minSelection(),r.maxSelection(),r.exposed());return GroupResponse.from(g,values.findByOptionGroupIdOrderBySortOrderAscIdAsc(id));}
    @Transactional public ValueResponse createValue(Long groupId,ValueRequest r){group(groupId);int order=values.findByOptionGroupIdOrderBySortOrderAscIdAsc(groupId).size()+1;ProductOptionValue v=values.save(ProductOptionValue.builder().optionGroupId(groupId).valueName(r.valueName().trim()).additionalPrice(r.additionalPrice()).inventoryManaged(r.inventoryManaged()).stockQuantity(r.stockQuantity()).saleStatus(status(r.saleStatus())).sortOrder(order).build());return ValueResponse.from(v);}
    @Transactional public ValueResponse updateValue(Long id,ValueRequest r){ProductOptionValue v=value(id);v.update(r.valueName().trim(),r.additionalPrice(),r.inventoryManaged(),r.stockQuantity(),status(r.saleStatus()));return ValueResponse.from(v);}
    @Transactional public void reorderGroups(Long productId,ReorderRequest r){List<ProductOptionGroup> current=groups.findByProductIdOrderBySortOrderAscIdAsc(productId);validateOrder(current.stream().map(ProductOptionGroup::getId).toList(),r.ids());for(int i=0;i<r.ids().size();i++)group(r.ids().get(i)).changeSortOrder(i+1);}
    @Transactional public void reorderValues(Long groupId,ReorderRequest r){List<ProductOptionValue> current=values.findByOptionGroupIdOrderBySortOrderAscIdAsc(groupId);validateOrder(current.stream().map(ProductOptionValue::getId).toList(),r.ids());for(int i=0;i<r.ids().size();i++)value(r.ids().get(i)).changeSortOrder(i+1);}
    @Transactional(readOnly=true) public List<AddonGroupResponse> addonGroups(Long productId){product(productId);return addonGroups.findByProductIdOrderBySortOrderAscIdAsc(productId).stream().map(this::addonResponse).toList();}
    @Transactional public AddonGroupResponse createAddonGroup(Long productId,AddonGroupRequest r){product(productId);validate(r.minQuantity(),r.maxQuantity());int order=addonGroups.findByProductIdOrderBySortOrderAscIdAsc(productId).size()+1;return addonResponse(addonGroups.save(ProductAddonGroup.builder().productId(productId).groupName(r.groupName().trim()).minQuantity(r.minQuantity()).maxQuantity(r.maxQuantity()).sortOrder(order).exposed(r.exposed()).build()));}
    @Transactional public AddonGroupResponse addAddon(Long groupId,AddonItemRequest r){ProductAddonGroup g=addonGroups.findById(groupId).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND));product(r.addonProductId());if(g.getProductId().equals(r.addonProductId()))throw validation("자기 자신은 추가상품으로 연결할 수 없습니다.");if(addonItems.findByAddonGroupIdAndAddonProductId(groupId,r.addonProductId()).isEmpty())addonItems.save(ProductAddonItem.builder().addonGroupId(groupId).addonProductId(r.addonProductId()).maxQuantity(r.maxQuantity()).sortOrder(addonItems.findByAddonGroupIdOrderBySortOrderAscIdAsc(groupId).size()+1).build());return addonResponse(g);}
    @Transactional public void removeAddon(Long groupId,Long productId){addonItems.deleteByAddonGroupIdAndAddonProductId(groupId,productId);}
    private AddonGroupResponse addonResponse(ProductAddonGroup g){List<AddonItemResponse> items=addonItems.findByAddonGroupIdOrderBySortOrderAscIdAsc(g.getId()).stream().map(i->{Product p=product(i.getAddonProductId());return new AddonItemResponse(i.getId(),p.getId(),p.getProductName(),p.getSalePrice(),i.getMaxQuantity(),i.getSortOrder());}).toList();return new AddonGroupResponse(g.getId(),g.getProductId(),g.getGroupName(),g.getMinQuantity(),g.getMaxQuantity(),g.getSortOrder(),g.isExposed(),items);}
    private Product product(Long id){return products.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND));}
    private ProductOptionGroup group(Long id){return groups.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND));}
    private ProductOptionValue value(Long id){return values.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND));}
    private OptionSelectionType type(String s){try{return OptionSelectionType.valueOf(s);}catch(Exception e){throw validation("선택 방식을 확인해 주세요.");}}
    private ProductSaleStatus status(String s){try{return ProductSaleStatus.valueOf(s);}catch(Exception e){throw validation("판매 상태를 확인해 주세요.");}}
    private void validate(int min,int max){if(min<0||max<1||min>max)throw validation("최소·최대 선택값을 확인해 주세요.");}
    private void validateOrder(List<Long> current,List<Long> requested){if(current.size()!=requested.size()||!new HashSet<>(current).equals(new HashSet<>(requested)))throw validation("정렬 대상이 현재 목록과 일치하지 않습니다.");}
    private ValidationBusinessException validation(String message){return new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,message);}
}
