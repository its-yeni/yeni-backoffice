package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 발주를 넣는 공급처(거래처). 리드타임은 발주 제안의 기본 리드타임 값으로 쓰인다. */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "supplier", uniqueConstraints = @UniqueConstraint(name = "uk_supplier_code", columnNames = "supplierCode"))
public class Supplier extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String supplierCode;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 60) private String managerName;
    @Column(length = 60) private String contact;
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("14") private int leadTimeDays;
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("true") private boolean active;
    @Column(length = 300) private String memo;

    public void update(String name, String managerName, String contact, int leadTimeDays, String memo) {
        this.name = name.trim();
        this.managerName = trimToNull(managerName);
        this.contact = trimToNull(contact);
        this.leadTimeDays = Math.max(1, leadTimeDays);
        this.memo = trimToNull(memo);
    }
    public void changeActive(boolean active) { this.active = active; }
    private static String trimToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
