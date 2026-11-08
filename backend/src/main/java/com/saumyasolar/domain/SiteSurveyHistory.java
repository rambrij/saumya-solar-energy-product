package com.saumyasolar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "site_survey_history")
@Getter
@Setter
@NoArgsConstructor
public class SiteSurveyHistory {
    @Id
    UUID id;
    UUID tenantId;
    UUID surveyId;
    UUID actorId;
    @Column(length = 40)
    String eventType;
    @Column(length = 30)
    String oldStatus;
    @Column(length = 30)
    String newStatus;
    @Column(length = 1000)
    String reason;
    Instant createdAt;
}
