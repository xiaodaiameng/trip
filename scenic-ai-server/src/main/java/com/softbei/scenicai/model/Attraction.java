package com.softbei.scenicai.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Attraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private String area;
    private String theme;
    private String intro;

    @Column(length = 1000)
    private String highlight;

    @Column(length = 4000)
    private String details;

    private String openHours;
    private Integer suggestedDurationMinutes;
    private Integer popularityScore;
    private Integer walkingIntensity;
    private Double latitude;
    private Double longitude;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attraction_tags")
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attraction_features")
    @Column(name = "feature")
    private List<String> features = new ArrayList<>();
}
