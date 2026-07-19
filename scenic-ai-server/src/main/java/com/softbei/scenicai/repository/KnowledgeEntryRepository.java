package com.softbei.scenicai.repository;

import com.softbei.scenicai.model.KnowledgeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, Long> {

    List<KnowledgeEntry> findAllByOrderByUpdatedAtDesc();

    Page<KnowledgeEntry> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    @Query("""
            select distinct entry
            from KnowledgeEntry entry
            left join entry.keywords keyword
            where entry.published = true
              and (
                   lower(:question) like concat('%', lower(entry.title), '%')
                or lower(:question) like concat('%', lower(entry.category), '%')
                or lower(:question) like concat('%', lower(keyword), '%')
              )
            order by entry.updatedAt desc
            """)
    List<KnowledgeEntry> searchPublishedCandidates(String question, Pageable pageable);
}
