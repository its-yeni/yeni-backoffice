package com.yeni.backoffice.core.payment.repository;

import com.yeni.backoffice.core.payment.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByJournalEntryIdInOrderByJournalEntryIdAscLineNoAsc(Collection<Long> journalEntryIds);

    List<JournalLine> findByAccountCodeOrderByJournalEntryIdAscLineNoAsc(String accountCode);
}
