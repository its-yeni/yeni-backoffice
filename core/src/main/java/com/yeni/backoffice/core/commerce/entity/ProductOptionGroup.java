package com.yeni.backoffice.core.commerce.entity;
import com.yeni.backoffice.core.commerce.enums.OptionSelectionType;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;import lombok.*;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="product_option_group")
public class ProductOptionGroup extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;@Column(nullable=false)private Long productId;@Column(nullable=false,length=100)private String groupName;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)private OptionSelectionType selectionType;@Column(nullable=false)private boolean requiredOption;@Column(nullable=false)private int minSelection;@Column(nullable=false)private int maxSelection;@Column(nullable=false)private int sortOrder;@Column(nullable=false)private boolean exposed;
 public void update(String n,OptionSelectionType t,boolean r,int min,int max,boolean e){groupName=n;selectionType=t;requiredOption=r;minSelection=min;maxSelection=max;exposed=e;}
 public void changeSortOrder(int n){sortOrder=n;}
}
