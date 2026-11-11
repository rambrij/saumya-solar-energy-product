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
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "content_type", nullable = false, length = 24)
    private String type;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(nullable = false, length = 180)
    private String slug;
    @Column(nullable = false, length = 20)
    String status = "DRAFT";
    @Column(name = "current_revision_id")
    private UUID currentRevisionId;
    @Column(name = "published_revision_id")
    private UUID publishedRevisionId;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;
}
