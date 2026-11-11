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
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "content_id", nullable = false)
    private UUID contentId;
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String body;
    @Column(name = "seo_title", length = 180)
    private String seoTitle;
    @Column(name = "seo_description", length = 320)
    private String seoDescription;
    @Column(name = "social_image_url", length = 1024)
    private String socialImageUrl;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
