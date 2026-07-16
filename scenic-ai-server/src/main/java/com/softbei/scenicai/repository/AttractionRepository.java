package com.softbei.scenicai.repository;

import com.softbei.scenicai.model.Attraction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttractionRepository extends JpaRepository<Attraction, Long> {
}
