package com.softbei.scenicai.repository;

import com.softbei.scenicai.model.FeedbackRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRecordRepository extends JpaRepository<FeedbackRecord, Long> {
}
