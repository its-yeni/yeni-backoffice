package com.yeni.backoffice.core.commerce.entity;
import com.yeni.backoffice.core.commerce.enums.OptionSelectionType;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;import lombok.*;
// 여러 상품에 재사용할 수 있는 옵션 템플릿. 상품에는 종속되지 않으며,
// 상품에 "적용"하면 ProductOptionGroup/ProductOptionValue로 값이 복사된다(스냅샷 방식).
// 재고·판매상태처럼 상품마다 달라지는 값은 템플릿에 두지 않는다.
@Getter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="option_group_template")
public class OptionGroupTemplate extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;@Column(nullable=false,length=100)private String templateName;@Column(name="customer_display_name",length=100)private String customerDisplayName;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)private OptionSelectionType selectionType;@Column(nullable=false)private boolean requiredOption;@Column(nullable=false)private int minSelection;@Column(nullable=false)private int maxSelection;@Column(nullable=false)private int sortOrder;
 public void update(String n,String d,OptionSelectionType t,boolean r,int min,int max){templateName=n;customerDisplayName=d;selectionType=t;requiredOption=r;minSelection=min;maxSelection=max;}
 public String displayName(){return customerDisplayName==null||customerDisplayName.isBlank()?templateName:customerDisplayName;}
}
