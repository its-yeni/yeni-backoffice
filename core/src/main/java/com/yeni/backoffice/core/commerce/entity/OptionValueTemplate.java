package com.yeni.backoffice.core.commerce.entity;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;import lombok.*;import java.math.BigDecimal;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="option_value_template")
public class OptionValueTemplate extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false)private Long templateGroupId;@Column(nullable=false,length=100)private String valueName;@Column(nullable=false,precision=19,scale=2)private BigDecimal defaultAdditionalPrice;@Column(nullable=false)private int sortOrder;
 public void update(String n,BigDecimal p){valueName=n;defaultAdditionalPrice=p;}
 public void changeSortOrder(int n){sortOrder=n;}
}
