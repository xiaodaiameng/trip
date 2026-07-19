package com.softbei.scenicai.repository;

import com.softbei.scenicai.model.ConversationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRecordRepository extends JpaRepository<ConversationRecord, Long> {

    List<ConversationRecord> findAllByOrderByCreatedAtDesc();

    Page<ConversationRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
