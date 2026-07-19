package com.softbei.scenicai.repository;

import com.softbei.scenicai.model.Attraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    List<Attraction> findAllByOrderByPopularityScoreDesc();

    Page<Attraction> findAllByOrderByPopularityScoreDesc(Pageable pageable);

    List<Attraction> findTop5ByOrderByPopularityScoreDesc();

    @Query("""
            select distinct attraction
            from Attraction attraction
            left join attraction.tags tag
            left join attraction.features feature
            where lower(:keyword) like concat('%', lower(attraction.name), '%')
               or lower(:keyword) like concat('%', lower(attraction.theme), '%')
               or lower(:keyword) like concat('%', lower(tag), '%')
               or lower(:keyword) like concat('%', lower(feature), '%')
            order by attraction.popularityScore desc
            """)
    List<Attraction> searchCandidates(String keyword, Pageable pageable);
}
