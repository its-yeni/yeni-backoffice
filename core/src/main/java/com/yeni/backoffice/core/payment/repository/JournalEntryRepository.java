package com.yeni.backoffice.core.payment.repository;

import com.yeni.backoffice.core.payment.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    boolean existsBySourceTypeAndSourceId(String sourceType, Long sourceId);

    List<JournalEntry> findBySourceType(String sourceType);

    Page<JournalEntry> findByEntryDateBetweenOrderByEntryDateDescIdDesc(LocalDate from, LocalDate to, Pageable pageable);

    List<JournalEntry> findByEntryDateLessThanEqual(LocalDate asOf);

    List<JournalEntry> findByIdIn(Set<Long> ids);
}
