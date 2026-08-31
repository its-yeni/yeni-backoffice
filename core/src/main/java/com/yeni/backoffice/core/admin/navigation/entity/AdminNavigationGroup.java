package com.yeni.backoffice.core.admin.navigation.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_navigation_group")
public class AdminNavigationGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String groupCode;

    @Column(nullable = false)
    private String groupName;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean useYn = true;

    public void update(String groupName, Integer sortOrder, boolean useYn) {
        this.groupName = groupName;
        this.sortOrder = sortOrder;
        this.useYn = useYn;
    }
}

