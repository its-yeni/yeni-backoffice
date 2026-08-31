package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.OptionTemplateDtos.*;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.OptionSelectionType;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

// 여러 상품에서 재사용하는 옵션 템플릿(그룹+기본값)을 관리한다.
// 실제 상품에는 ProductOptionService.applyTemplate()이 값을 복사해서 반영한다(스냅샷 방식).
@Service
public class OptionTemplateService {
    private final OptionGroupTemplateRepository groups; private final OptionValueTemplateRepository values;
    public OptionTemplateService(OptionGroupTemplateRepository g,OptionValueTemplateRepository v){groups=g;values=v;}

    @Transactional(readOnly=true) public List<TemplateGroupResponse> list(){return groups.findAllByOrderBySortOrderAscIdAsc().stream().map(g->TemplateGroupResponse.from(g,values.findByTemplateGroupIdOrderBySortOrderAscIdAsc(g.getId()))).toList();}

    // 그룹과 초기 옵션값들을 한 트랜잭션으로 같이 저장한다. 값 저장 중 하나라도 실패하면 그룹 생성도 롤백된다.
    @Transactional public TemplateGroupResponse create(TemplateGroupRequest r){
        validate(r.minSelection(),r.maxSelection());
        int order=groups.findAllByOrderBySortOrderAscIdAsc().size()+1;
        OptionGroupTemplate g=groups.save(OptionGroupTemplate.builder().templateName(r.templateName().trim()).customerDisplayName(displayName(r)).selectionType(type(r.selectionType())).requiredOption(r.requiredOption()).minSelection(r.minSelection()).maxSelection(r.maxSelection()).sortOrder(order).build());
        List<TemplateValueResponse> created=new ArrayList<>();
        int valueOrder=1;
        for(TemplateValueRequest vr:r.values()){
            if(vr.valueName()==null||vr.valueName().isBlank())continue;
            created.add(TemplateValueResponse.from(values.save(OptionValueTemplate.builder().templateGroupId(g.getId()).valueName(vr.valueName().trim()).defaultAdditionalPrice(vr.defaultAdditionalPrice()).sortOrder(valueOrder++).build())));
        }
        return new TemplateGroupResponse(g.getId(),g.getTemplateName(),g.displayName(),g.getSelectionType().name(),g.isRequiredOption(),g.getMinSelection(),g.getMaxSelection(),g.getSortOrder(),created);
    }

    @Transactional public TemplateGroupResponse update(Long id,TemplateGroupRequest r){
        validate(r.minSelection(),r.maxSelection());
        OptionGroupTemplate g=group(id);
        g.update(r.templateName().trim(),displayName(r),type(r.selectionType()),r.requiredOption(),r.minSelection(),r.maxSelection());
        return TemplateGroupResponse.from(g,values.findByTemplateGroupIdOrderBySortOrderAscIdAsc(id));
    }

    @Transactional public void delete(Long id){group(id);values.deleteByTemplateGroupId(id);groups.deleteById(id);}

    @Transactional public TemplateValueResponse createValue(Long templateGroupId,TemplateValueRequest r){
        group(templateGroupId);
        int order=values.findByTemplateGroupIdOrderBySortOrderAscIdAsc(templateGroupId).size()+1;
        OptionValueTemplate v=values.save(OptionValueTemplate.builder().templateGroupId(templateGroupId).valueName(r.valueName().trim()).defaultAdditionalPrice(r.defaultAdditionalPrice()).sortOrder(order).build());
        return TemplateValueResponse.from(v);
    }

    @Transactional public TemplateValueResponse updateValue(Long id,TemplateValueRequest r){
        OptionValueTemplate v=value(id);
        v.update(r.valueName().trim(),r.defaultAdditionalPrice());
        return TemplateValueResponse.from(v);
    }

    @Transactional public void deleteValue(Long id){value(id);values.deleteById(id);}

    // ProductOptionService.applyTemplate()에서 사용하는 조회 전용 헬퍼.
    OptionGroupTemplate group(Long id){return groups.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND));}
    List<OptionValueTemplate> valuesOf(Long templateGroupId){return values.findByTemplateGroupIdOrderBySortOrderAscIdAsc(templateGroupId);}

    private OptionValueTemplate value(Long id){return values.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND));}
    private OptionSelectionType type(String s){try{return OptionSelectionType.valueOf(s);}catch(Exception e){throw validation("선택 방식을 확인해 주세요.");}}
    private void validate(int min,int max){if(min<0||max<1||min>max)throw validation("최소·최대 선택값을 확인해 주세요.");}
    private ValidationBusinessException validation(String message){return new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,message);}
    private String displayName(TemplateGroupRequest r){return r.customerDisplayName()==null||r.customerDisplayName().isBlank()?r.templateName().trim():r.customerDisplayName().trim();}
}
