package com.yeni.backoffice.core.admin.navigation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class SidebarNavigationItemDto {
    private Long id;
    private String itemName;
    private String itemUrl;
    private String icon;
    private Integer sortOrder;
    private boolean active;
    /** 업무 흐름 하위 단계(예: 재고·발주 → 발주서 / 입고 검수). 없으면 빈 리스트. */
    private List<SidebarNavigationItemDto> children;

    public SidebarNavigationItemDto(Long id, String itemName, String itemUrl, String icon,
                                    Integer sortOrder, boolean active) {
        this(id, itemName, itemUrl, icon, sortOrder, active, Collections.emptyList());
    }

    public boolean isSectionActive() {
        if (active) {
            return true;
        }
        return children.stream().anyMatch(SidebarNavigationItemDto::isActive);
    }
}
