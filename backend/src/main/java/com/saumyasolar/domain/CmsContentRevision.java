package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cms_content_revision", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cms_revision_tenant_id", columnNames = {"tenant_id", "id"}),
        @UniqueConstraint(name = "uq_cms_revision_number", columnNames = {"tenant_id", "content_id", "revision_number"})
})
@Getter
@Setter
@NoArgsConstructor
public class CmsContentRevision {
    @Id
    UUID id;
    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;
    @Column(name = "content_id", nullable = false)
    UUID contentId;
    @Column(name = "revision_number", nullable = false)
    int revisionNumber;
    @Column(nullable = false, length = 180)
    String title;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    String body;
    @Column(name = "seo_title", length = 180)
    String seoTitle;
    @Column(name = "seo_description", length = 320)
    String seoDescription;
    @Column(name = "social_image_url", length = 1024)
    String socialImageUrl;
    @Column(name = "created_by", nullable = false)
    UUID createdBy;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
