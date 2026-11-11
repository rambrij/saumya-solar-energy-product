package com.saumyasolar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cms_content", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cms_content_tenant_id", columnNames = {"tenant_id", "id"}),
        @UniqueConstraint(name = "uq_cms_content_tenant_type_slug", columnNames = {"tenant_id", "content_type", "slug"})
})
@Getter
@Setter
@NoArgsConstructor
public class CmsContent {
    @Id
    UUID id;
    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;
    @Column(name = "content_type", nullable = false, length = 24)
    String type;
    @Column(nullable = false, length = 180)
    String title;
    @Column(nullable = false, length = 180)
    String slug;
    @Column(nullable = false, length = 20)
    String status = "DRAFT";
    @Column(name = "current_revision_id")
    UUID currentRevisionId;
    @Column(name = "published_revision_id")
    UUID publishedRevisionId;
    @Column(name = "published_at")
    Instant publishedAt;
    @Column(name = "created_by", nullable = false)
    UUID createdBy;
    @Column(name = "updated_by", nullable = false)
    UUID updatedBy;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
    @Version
    @Column(nullable = false)
    long version;
}
