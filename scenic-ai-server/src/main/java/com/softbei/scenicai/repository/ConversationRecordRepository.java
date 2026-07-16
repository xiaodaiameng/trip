package com.softbei.scenicai.repository;

import com.softbei.scenicai.model.ConversationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRecordRepository extends JpaRepository<ConversationRecord, Long> {
}
