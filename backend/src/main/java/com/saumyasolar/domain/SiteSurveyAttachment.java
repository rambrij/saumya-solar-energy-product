package com.saumyasolar.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "site_survey_attachment")
@Getter
@Setter
@NoArgsConstructor
public class SiteSurveyAttachment {
    @Id
    UUID id;
    UUID tenantId;
    UUID surveyId;
    String originalName;
    String storageKey;
    String contentType;
    long sizeBytes;
    UUID uploadedBy;
    Instant createdAt;
}
