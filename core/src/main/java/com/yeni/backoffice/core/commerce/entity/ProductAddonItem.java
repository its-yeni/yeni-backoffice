package com.yeni.backoffice.core.commerce.entity;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;import jakarta.persistence.*;import lombok.*;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="product_addon_item",uniqueConstraints=@UniqueConstraint(name="uk_addon_group_product",columnNames={"addonGroupId","addonProductId"}))
public class ProductAddonItem extends BaseTimeEntity{@Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false)private Long addonGroupId;@Column(nullable=false)private Long addonProductId;@Column(nullable=false)private int maxQuantity;@Column(nullable=false)private int sortOrder;public void update(int max){maxQuantity=max;}}
