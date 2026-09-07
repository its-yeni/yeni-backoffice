package com.yeni.backoffice.core.admin.navigation.repository;

import com.yeni.backoffice.core.admin.navigation.entity.AdminNavigationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminNavigationItemRepository extends JpaRepository<AdminNavigationItem, Long> {
    
    @Query("""
        SELECT ni FROM AdminNavigationItem ni
        WHERE ni.isDeleted = false
        AND ni.useYn = true
        AND ni.displayYn = true
        ORDER BY ni.navigationGroup.sortOrder ASC, ni.sortOrder ASC, ni.id ASC
        """)
    List<AdminNavigationItem> findForSidebar();

    @Query("""
        SELECT ni FROM AdminNavigationItem ni
        WHERE ni.isDeleted = false
        ORDER BY ni.navigationGroup.sortOrder ASC, ni.sortOrder ASC, ni.id ASC
        """)
    List<AdminNavigationItem> findAllNotDeleted();

    @Query("""
        SELECT ni FROM AdminNavigationItem ni
        WHERE ni.isDeleted = false
        AND ni.useYn = true
        ORDER BY ni.navigationGroup.sortOrder ASC, ni.sortOrder ASC, ni.id ASC
        """)
    List<AdminNavigationItem> findAllUsable();

    boolean existsByItemUrl(String itemUrl);
    boolean existsByItemUrlAndIsDeletedFalse(String itemUrl);
    Optional<AdminNavigationItem> findByItemUrlAndIsDeletedFalse(String itemUrl);
}

