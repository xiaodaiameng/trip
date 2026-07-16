package com.softbei.scenicai.repository;

import com.softbei.scenicai.model.KnowledgeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, Long> {
}
