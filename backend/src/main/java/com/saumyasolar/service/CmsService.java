package com.saumyasolar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.AuditEvent;
import com.saumyasolar.domain.CmsContent;
import com.saumyasolar.domain.CmsContentRevision;
import com.saumyasolar.repo.AuditEventRepository;
import com.saumyasolar.repo.CmsContentRepository;
import com.saumyasolar.repo.CmsContentRevisionRepository;
import com.saumyasolar.repo.TenantRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CmsService {
    private static final Set<String> TYPES = Set.of("PAGE", "BANNER", "FAQ", "TESTIMONIAL", "BLOG");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private final CmsContentRepository contents;
    private final CmsContentRevisionRepository revisions;
    private final TenantRepository tenants;
    private final AuditEventRepository audits;
    private final ObjectMapper mapper;

    public CmsService(CmsContentRepository contents, CmsContentRevisionRepository revisions,
                      TenantRepository tenants, AuditEventRepository audits, ObjectMapper mapper) {
        this.contents = contents;
        this.revisions = revisions;
        this.tenants = tenants;
        this.audits = audits;
        this.mapper = mapper;
    }

    @Transactional
    public CmsContent create(ContentInput input, Principal actor) {
        UUID tenantId = requireVendor(actor, "CMS_CREATE");
        validateInput(input);
        String type = normalizeType(input.type());
        String slug = normalizeSlug(input.slug());
        if (contents.existsByTenantIdAndTypeAndSlug(tenantId, type, slug))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A content item already uses this slug");
        Instant now = Instant.now();
        CmsContent content = new CmsContent();
        content.setId(UUID.randomUUID());
        content.setTenantId(tenantId);
        content.setType(type);
        content.setTitle(input.title().trim());
        content.setSlug(slug);
        content.setStatus("DRAFT");
        content.setCreatedBy(actor.userId());
        content.setUpdatedBy(actor.userId());
        content.setCreatedAt(now);
        content.setUpdatedAt(now);
        contents.save(content);
        CmsContentRevision revision = revision(content, input, actor.userId(), 1, now);
        revisions.save(revision);
        content.setCurrentRevisionId(revision.getId());
        contents.save(content);
        audit(actor, tenantId, "CMS_CONTENT_CREATED", content, null, Map.of("status", "DRAFT", "revision", 1));
        return content;
    }

    public Page<CmsContent> list(Principal actor, String type, int page, int size) {
        UUID tenantId = requireVendor(actor, "CMS_VIEW");
        validatePage(page, size);
        if (type == null || type.isBlank())
            return contents.findByTenantIdOrderByUpdatedAtDesc(tenantId, PageRequest.of(page, size));
        return contents.findByTenantIdAndTypeOrderByUpdatedAtDesc(tenantId, normalizeType(type),
                PageRequest.of(page, size));
    }

    public ContentDetail get(UUID id, Principal actor) {
        UUID tenantId = requireVendor(actor, "CMS_VIEW");
        CmsContent content = requireContent(id, tenantId);
        CmsContentRevision revision = currentRevision(content);
        return new ContentDetail(content, revision, revisions.findByTenantIdAndContentIdOrderByRevisionNumberDesc(tenantId, id));
    }

    @Transactional
    public CmsContent update(UUID id, ContentInput input, Principal actor) {
        UUID tenantId = requireVendor(actor, "CMS_UPDATE");
        CmsContent content = requireContent(id, tenantId);
        validateInput(input);
        String type = normalizeType(input.type());
        String slug = normalizeSlug(input.slug());
        if (contents.existsByTenantIdAndTypeAndSlugAndIdNot(tenantId, type, slug, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A content item already uses this slug");
        String beforeStatus = content.getStatus();
        CmsContentRevision previous = currentRevision(content);
        int nextRevision = previous.getRevisionNumber() + 1;
        CmsContentRevision revision = revision(content, input, actor.userId(), nextRevision, Instant.now());
        revisions.save(revision);
        content.setType(type);
        content.setTitle(input.title().trim());
        content.setSlug(slug);
        content.setCurrentRevisionId(revision.getId());
        content.setStatus("DRAFT");
        content.setUpdatedBy(actor.userId());
        content.setUpdatedAt(Instant.now());
        contents.save(content);
        audit(actor, tenantId, "CMS_CONTENT_UPDATED", content,
                Map.of("status", beforeStatus, "revision", previous.getRevisionNumber()),
                Map.of("status", "DRAFT", "revision", nextRevision));
        return content;
    }

    @Transactional
    public CmsContent transition(UUID id, String targetStatus, Principal actor) {
        UUID tenantId = requireVendor(actor, "CMS_UPDATE");
        CmsContent content = requireContent(id, tenantId);
        String target = targetStatus == null ? "" : targetStatus.trim().toUpperCase(Locale.ROOT);
        String current = content.getStatus();
        boolean allowed = ("DRAFT".equals(current) && ("REVIEW".equals(target) || "ARCHIVED".equals(target)))
                || ("REVIEW".equals(current) && ("DRAFT".equals(target) || "PUBLISHED".equals(target) || "ARCHIVED".equals(target)))
                || ("PUBLISHED".equals(current) && "ARCHIVED".equals(target));
        if (!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid CMS content transition");
        if ("PUBLISHED".equals(target)) requirePermission(actor, "CMS_PUBLISH");
        if ("ARCHIVED".equals(target)) requirePermission(actor, "CMS_PUBLISH");
        if ("PUBLISHED".equals(target)) {
            CmsContentRevision currentRevision = currentRevision(content);
            content.setPublishedRevisionId(currentRevision.getId());
            content.setPublishedAt(Instant.now());
        }
        if ("ARCHIVED".equals(target)) {
            content.setPublishedRevisionId(null);
            content.setPublishedAt(null);
        }
        content.setStatus(target);
        content.setUpdatedBy(actor.userId());
        content.setUpdatedAt(Instant.now());
        contents.save(content);
        audit(actor, tenantId, "CMS_CONTENT_" + target, content,
                Map.of("status", current), Map.of("status", target));
        return content;
    }

    @Transactional
    public CmsContent rollback(UUID id, int revisionNumber, Principal actor) {
        UUID tenantId = requireVendor(actor, "CMS_UPDATE");
        CmsContent content = requireContent(id, tenantId);
        CmsContentRevision source = revisions.findByTenantIdAndContentIdOrderByRevisionNumberDesc(tenantId, id)
                .stream().filter(row -> row.getRevisionNumber() == revisionNumber).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content revision not found"));
        int next = revisions.findTopByTenantIdAndContentIdOrderByRevisionNumberDesc(tenantId, id)
                .map(CmsContentRevision::getRevisionNumber).orElse(0) + 1;
        CmsContentRevision restored = new CmsContentRevision();
        restored.setId(UUID.randomUUID());
        restored.setTenantId(tenantId);
        restored.setContentId(id);
        restored.setRevisionNumber(next);
        restored.setTitle(source.getTitle());
        restored.setBody(source.getBody());
        restored.setSeoTitle(source.getSeoTitle());
        restored.setSeoDescription(source.getSeoDescription());
        restored.setSocialImageUrl(source.getSocialImageUrl());
        restored.setCreatedBy(actor.userId());
        restored.setCreatedAt(Instant.now());
        revisions.save(restored);
        content.setTitle(restored.getTitle());
        content.setCurrentRevisionId(restored.getId());
        content.setStatus("DRAFT");
        content.setUpdatedBy(actor.userId());
        content.setUpdatedAt(Instant.now());
        contents.save(content);
        audit(actor, tenantId, "CMS_CONTENT_ROLLED_BACK", content,
                Map.of("sourceRevision", revisionNumber), Map.of("newRevision", next));
        return content;
    }

    public Page<PublicContent> publicList(String storeSlug, String type, int page, int size) {
        validatePage(page, size);
        var tenant = tenants.findBySlug(storeSlug)
                .filter(row -> "ACTIVE".equals(row.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
        var result = contents.findByTenantIdAndTypeAndPublishedRevisionIdIsNotNullAndStatusNotOrderByPublishedAtDesc(
                tenant.getId(), normalizeType(type), "ARCHIVED",
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt")));
        return result.map(this::publicViewIfPublished);
    }

    public PublicContent publicGet(String storeSlug, String type, String slug) {
        var tenant = tenants.findBySlug(storeSlug)
                .filter(row -> "ACTIVE".equals(row.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
        CmsContent content = contents.findByTenantIdAndTypeAndSlugAndPublishedRevisionIdIsNotNull(
                        tenant.getId(), normalizeType(type), normalizeSlug(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
        if ("ARCHIVED".equals(content.getStatus()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        CmsContentRevision revision = revisions.findByIdAndTenantIdAndContentId(
                        content.getPublishedRevisionId(), tenant.getId(), content.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
        return publicView(content, revision);
    }

    private PublicContent publicViewIfPublished(CmsContent content) {
        if (content.getPublishedRevisionId() == null || "ARCHIVED".equals(content.getStatus()))
            return null;
        CmsContentRevision revision = revisions.findByIdAndTenantIdAndContentId(
                        content.getPublishedRevisionId(), content.getTenantId(), content.getId())
                .orElse(null);
        return revision == null ? null : publicView(content, revision);
    }

    private PublicContent publicView(CmsContent content, CmsContentRevision revision) {
        return new PublicContent(content.getType(), content.getSlug(), revision.getTitle(), revision.getBody(),
                revision.getSeoTitle(), revision.getSeoDescription(), revision.getSocialImageUrl(), content.getPublishedAt());
    }

    private CmsContentRevision currentRevision(CmsContent content) {
        return revisions.findByIdAndTenantIdAndContentId(content.getCurrentRevisionId(),
                        content.getTenantId(), content.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content revision not found"));
    }

    private CmsContentRevision revision(CmsContent content, ContentInput input, UUID actorId, int number, Instant now) {
        CmsContentRevision revision = new CmsContentRevision();
        revision.setId(UUID.randomUUID());
        revision.setTenantId(content.getTenantId());
        revision.setContentId(content.getId());
        revision.setRevisionNumber(number);
        revision.setTitle(input.title().trim());
        revision.setBody(input.body().trim());
        revision.setSeoTitle(trim(input.seoTitle()));
        revision.setSeoDescription(trim(input.seoDescription()));
        revision.setSocialImageUrl(validateImageUrl(input.socialImageUrl()));
        revision.setCreatedBy(actorId);
        revision.setCreatedAt(now);
        return revision;
    }

    private UUID requireVendor(Principal actor, String permission) {
        if (actor == null || !"VENDOR".equals(actor.accountType()) || actor.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        requirePermission(actor, permission);
        return actor.tenantId();
    }

    private void requirePermission(Principal actor, String code) {
        if (actor.permissions() == null || !actor.permissions().contains(code))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + code);
    }

    private CmsContent requireContent(UUID id, UUID tenantId) {
        return contents.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
    }

    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported CMS content type");
        return type;
    }

    private String normalizeSlug(String value) {
        String slug = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (slug.length() > 180 || !SLUG.matcher(slug).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slug must contain lowercase letters, numbers and single hyphens");
        return slug;
    }

    private void validateInput(ContentInput input) {
        if (input == null || input.title() == null || input.title().isBlank() || input.title().length() > 180
                || input.body() == null || input.body().isBlank() || input.body().length() > 100000
                || input.body().matches("(?is).*<[^>]+>.*")
                || (input.seoTitle() != null && input.seoTitle().length() > 180)
                || (input.seoDescription() != null && input.seoDescription().length() > 320))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CMS content fields are invalid");
    }

    private String validateImageUrl(String value) {
        String image = trim(value);
        if (image == null) return null;
        try {
            URI uri = URI.create(image);
            if (uri.isAbsolute() && !"https".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException();
            if (image.startsWith("//") || image.toLowerCase(Locale.ROOT).startsWith("javascript:"))
                throw new IllegalArgumentException();
            return image;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Social image must be an HTTPS or application-relative URL");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(Principal actor, UUID tenantId, String action, CmsContent content, Object before, Object after) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setActorId(actor.userId());
        event.setActorType(actor.accountType());
        event.setAction(action);
        event.setEntityType("CMS_CONTENT");
        event.setEntityId(content.getId().toString());
        event.setOutcome("SUCCESS");
        event.setBeforeJson(json(before));
        event.setAfterJson(json(after));
        event.setCreatedAt(Instant.now());
        audits.save(event);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { return "{}"; }
    }

    public record ContentInput(String type, String title, String slug, String body,
                               String seoTitle, String seoDescription, String socialImageUrl) { }
    public record ContentDetail(CmsContent content, CmsContentRevision revision,
                                java.util.List<CmsContentRevision> versions) { }
    public record PublicContent(String type, String slug, String title, String body,
                                String seoTitle, String seoDescription, String socialImageUrl, Instant publishedAt) { }
}
