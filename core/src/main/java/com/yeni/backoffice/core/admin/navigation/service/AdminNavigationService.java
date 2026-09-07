package com.yeni.backoffice.core.admin.navigation.service;

import com.yeni.backoffice.core.admin.auth.AdminRole;
import com.yeni.backoffice.core.admin.navigation.dto.AdminNavigationGroupOptionDto;
import com.yeni.backoffice.core.admin.navigation.dto.AdminNavigationListItemDto;
import com.yeni.backoffice.core.admin.navigation.dto.AdminNavigationSaveRequest;
import com.yeni.backoffice.core.admin.navigation.dto.AdminNavigationUpdateRequest;
import com.yeni.backoffice.core.admin.navigation.dto.SidebarNavigationGroupDto;
import com.yeni.backoffice.core.admin.navigation.dto.SidebarNavigationItemDto;
import com.yeni.backoffice.core.admin.navigation.entity.AdminNavigationGroup;
import com.yeni.backoffice.core.admin.navigation.entity.AdminNavigationItem;
import com.yeni.backoffice.core.admin.navigation.repository.AdminNavigationGroupRepository;
import com.yeni.backoffice.core.admin.navigation.repository.AdminNavigationItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminNavigationService {

    private final AdminNavigationGroupRepository navigationGroupRepository;
    private final AdminNavigationItemRepository navigationItemRepository;

    public AdminNavigationService(
            AdminNavigationGroupRepository navigationGroupRepository,
            AdminNavigationItemRepository navigationItemRepository) {
        this.navigationGroupRepository = navigationGroupRepository;
        this.navigationItemRepository = navigationItemRepository;
    }

    @Transactional(readOnly = true)
    public List<SidebarNavigationGroupDto> getSidebarNavigationGroups(String currentPath, AdminRole currentRole) {
        AdminRole role = currentRole == null ? AdminRole.USER : currentRole;
        List<AdminNavigationGroup> groups = navigationGroupRepository.findByUseYnTrueOrderBySortOrderAscIdAsc();

        // useYn=true 인 항목 전체를 가져와 부모/자식으로 나눈다. 최상위(사이드바 노출)는
        // displayYn=true + 부모 없음, 하위 단계는 부모가 최상위인 항목(displayYn 무관 —
        // 숨긴 화면도 업무 흐름 단계로 노출한다).
        List<AdminNavigationItem> usable = navigationItemRepository.findAllUsable().stream()
                .filter(item -> role.canAccess(item.getRequiredRole()))
                .collect(Collectors.toList());

        Map<Long, List<AdminNavigationItem>> childrenByParent = new HashMap<>();
        for (AdminNavigationItem item : usable) {
            if (item.getParentNavigationItemId() != null) {
                childrenByParent.computeIfAbsent(item.getParentNavigationItemId(), k -> new ArrayList<>()).add(item);
            }
        }

        Map<Long, List<AdminNavigationItem>> topLevelByGroup = new HashMap<>();
        for (AdminNavigationItem item : usable) {
            if (item.getParentNavigationItemId() == null && Boolean.TRUE.equals(item.getDisplayYn())) {
                topLevelByGroup.computeIfAbsent(item.getNavigationGroup().getId(), k -> new ArrayList<>()).add(item);
            }
        }

        return groups.stream()
                .map(group -> new SidebarNavigationGroupDto(
                        group.getGroupCode(),
                        group.getGroupName(),
                        group.getSortOrder(),
                        topLevelByGroup.getOrDefault(group.getId(), Collections.emptyList()).stream()
                                .map(item -> new SidebarNavigationItemDto(
                                        item.getId(),
                                        item.getItemName(),
                                        item.getItemUrl(),
                                        item.getIcon(),
                                        item.getSortOrder(),
                                        isActivePath(item.getItemUrl(), currentPath),
                                        childrenByParent.getOrDefault(item.getId(), Collections.emptyList()).stream()
                                                .map(child -> new SidebarNavigationItemDto(
                                                        child.getId(), child.getItemName(), child.getItemUrl(),
                                                        child.getIcon(), child.getSortOrder(),
                                                        isActivePath(child.getItemUrl(), currentPath)))
                                                .collect(Collectors.toList())
                                ))
                                .collect(Collectors.toList())
                ))
                .filter(group -> !group.getItems().isEmpty())
                .collect(Collectors.toList());
    }

    /** "전체 기능" 페이지 — 사이드바 노출 여부와 무관하게 사용 가능한 모든 화면을 그룹별로. */
    @Transactional(readOnly = true)
    public List<SidebarNavigationGroupDto> getAllFeatureGroups(AdminRole currentRole) {
        AdminRole role = currentRole == null ? AdminRole.USER : currentRole;
        List<AdminNavigationGroup> groups = navigationGroupRepository.findByUseYnTrueOrderBySortOrderAscIdAsc();
        Map<Long, List<AdminNavigationItem>> byGroup = new HashMap<>();
        for (AdminNavigationItem item : navigationItemRepository.findAllUsable()) {
            if (!role.canAccess(item.getRequiredRole())) continue;
            byGroup.computeIfAbsent(item.getNavigationGroup().getId(), k -> new ArrayList<>()).add(item);
        }
        return groups.stream()
                .map(group -> new SidebarNavigationGroupDto(
                        group.getGroupCode(), group.getGroupName(), group.getSortOrder(),
                        byGroup.getOrDefault(group.getId(), Collections.emptyList()).stream()
                                .map(item -> new SidebarNavigationItemDto(
                                        item.getId(), item.getItemName(), item.getItemUrl(),
                                        item.getIcon(), item.getSortOrder(), false))
                                .collect(Collectors.toList())))
                .filter(group -> !group.getItems().isEmpty())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AdminNavigationListItemDto> getNavigationListForAdmin() {
        return navigationItemRepository.findAllNotDeleted().stream()
                .map(this::toListItemDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminNavigationListItemDto getNavigationItemForAdmin(Long navigationItemId) {
        return toListItemDto(findItem(navigationItemId));
    }

    @Transactional(readOnly = true)
    public List<AdminNavigationGroupOptionDto> getNavigationGroupOptions() {
        return navigationGroupRepository.findByUseYnTrueOrderBySortOrderAscIdAsc().stream()
                .map(group -> new AdminNavigationGroupOptionDto(
                        group.getId(),
                        group.getGroupCode(),
                        group.getGroupName()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public void saveNavigation(AdminNavigationSaveRequest request) {
        validateSaveRequest(request);
        validateDuplicateItemUrl(request.getItemUrl(), null);

        AdminNavigationGroup group = findGroup(request.getNavigationGroupId());
        AdminNavigationItem item = AdminNavigationItem.builder()
                .navigationGroup(group)
                .parentNavigationItemId(request.getParentNavigationItemId())
                .itemName(request.getItemName().trim())
                .itemUrl(normalizeItemUrl(request.getItemUrl()))
                .icon(normalizeNullableText(request.getIcon()))
                .depth(defaultNumber(request.getDepth(), 1))
                .sortOrder(defaultNumber(request.getSortOrder(), 1))
                .useYn(defaultBoolean(request.getUseYn(), true))
                .displayYn(defaultBoolean(request.getDisplayYn(), true))
                .requiredRole(defaultRole(request.getRequiredRole()))
                .build();

        navigationItemRepository.save(item);
    }

    @Transactional
    public void updateNavigation(AdminNavigationUpdateRequest request) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("Navigation item id is required.");
        }
        validateUpdateRequest(request);
        validateDuplicateItemUrl(request.getItemUrl(), request.getId());

        AdminNavigationItem item = findItem(request.getId());
        AdminNavigationGroup group = findGroup(request.getNavigationGroupId());
        item.update(
                group,
                request.getParentNavigationItemId(),
                request.getItemName().trim(),
                normalizeItemUrl(request.getItemUrl()),
                normalizeNullableText(request.getIcon()),
                defaultNumber(request.getDepth(), 1),
                defaultNumber(request.getSortOrder(), 1),
                defaultBoolean(request.getUseYn(), true),
                defaultBoolean(request.getDisplayYn(), true),
                defaultRole(request.getRequiredRole())
        );
    }

    @Transactional
    public void updateSortOrder(Long navigationItemId, Integer sortOrder) {
        findItem(navigationItemId).updateSortOrder(defaultNumber(sortOrder, 1));
    }

    @Transactional
    public void updateDisplayYn(Long navigationItemId, Boolean displayYn) {
        findItem(navigationItemId).updateDisplayYn(defaultBoolean(displayYn, true));
    }

    @Transactional
    public void updateUseYn(Long navigationItemId, Boolean useYn) {
        findItem(navigationItemId).updateUseYn(defaultBoolean(useYn, true));
    }

    @Transactional
    public void softDeleteNavigation(Long navigationItemId) {
        findItem(navigationItemId).softDelete();
    }

    private AdminNavigationListItemDto toListItemDto(AdminNavigationItem item) {
        return new AdminNavigationListItemDto(
                item.getId(),
                item.getNavigationGroup().getId(),
                item.getNavigationGroup().getGroupCode(),
                item.getNavigationGroup().getGroupName(),
                item.getParentNavigationItemId(),
                item.getIcon(),
                item.getItemName(),
                item.getItemUrl(),
                item.getDepth(),
                item.getSortOrder(),
                item.getUseYn(),
                item.getDisplayYn(),
                item.getRequiredRole()
        );
    }

    private boolean isActivePath(String itemUrl, String currentPath) {
        if (currentPath == null || itemUrl == null) {
            return false;
        }
        return currentPath.equals(itemUrl) || currentPath.startsWith(itemUrl + "/");
    }

    private AdminNavigationGroup findGroup(Long navigationGroupId) {
        if (navigationGroupId == null) {
            throw new IllegalArgumentException("Navigation group is required.");
        }
        return navigationGroupRepository.findById(navigationGroupId)
                .orElseThrow(() -> new IllegalArgumentException("Navigation group not found."));
    }

    private AdminNavigationItem findItem(Long navigationItemId) {
        return navigationItemRepository.findById(navigationItemId)
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Navigation item not found."));
    }

    private void validateSaveRequest(AdminNavigationSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required.");
        }
        validateRequiredFields(request.getNavigationGroupId(), request.getItemName(), request.getItemUrl());
    }

    private void validateUpdateRequest(AdminNavigationUpdateRequest request) {
        validateRequiredFields(request.getNavigationGroupId(), request.getItemName(), request.getItemUrl());
    }

    private void validateRequiredFields(Long navigationGroupId, String itemName, String itemUrl) {
        if (navigationGroupId == null) {
            throw new IllegalArgumentException("Navigation group is required.");
        }
        if (!StringUtils.hasText(itemName)) {
            throw new IllegalArgumentException("Item name is required.");
        }
        if (!StringUtils.hasText(itemUrl)) {
            throw new IllegalArgumentException("Item URL is required.");
        }
    }

    private void validateDuplicateItemUrl(String itemUrl, Long currentId) {
        String normalizedItemUrl = normalizeItemUrl(itemUrl);
        boolean duplicated = navigationItemRepository.findAllNotDeleted().stream()
                .anyMatch(item -> normalizedItemUrl.equals(item.getItemUrl())
                        && !Objects.equals(item.getId(), currentId));

        if (duplicated) {
            throw new IllegalArgumentException("Item URL already exists.");
        }
    }

    private String normalizeItemUrl(String itemUrl) {
        String normalized = itemUrl.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizeNullableText(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private Integer defaultNumber(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Boolean defaultBoolean(Boolean value, Boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private AdminRole defaultRole(AdminRole role) {
        return role == null ? AdminRole.USER : role;
    }
}
