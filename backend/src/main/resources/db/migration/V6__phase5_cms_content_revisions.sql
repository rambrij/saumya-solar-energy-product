-- Phase 5 CMS content and immutable revision history.
-- Additive only; no existing production rows or historical content are backfilled.
CREATE TABLE cms_content (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  content_type VARCHAR(24) NOT NULL,
  title VARCHAR(180) NOT NULL,
  slug VARCHAR(180) NOT NULL,
  status VARCHAR(20) NOT NULL,
  current_revision_id BINARY(16) NULL,
  published_revision_id BINARY(16) NULL,
  published_at TIMESTAMP(6) NULL,
  created_by BINARY(16) NOT NULL,
  updated_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_cms_content_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT uq_cms_content_tenant_type_slug UNIQUE (tenant_id,content_type,slug),
  CONSTRAINT fk_cms_content_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_cms_content_created_by FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id),
  CONSTRAINT fk_cms_content_updated_by FOREIGN KEY (tenant_id,updated_by) REFERENCES app_user(tenant_id,id),
  INDEX ix_cms_content_public (tenant_id,content_type,status,published_at),
  INDEX ix_cms_content_updated (tenant_id,updated_at)
);

CREATE TABLE cms_content_revision (
  id BINARY(16) PRIMARY KEY,
  tenant_id BINARY(16) NOT NULL,
  content_id BINARY(16) NOT NULL,
  revision_number INT NOT NULL,
  title VARCHAR(180) NOT NULL,
  body LONGTEXT NOT NULL,
  seo_title VARCHAR(180) NULL,
  seo_description VARCHAR(320) NULL,
  social_image_url VARCHAR(1024) NULL,
  created_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uq_cms_revision_tenant_id UNIQUE (tenant_id,id),
  CONSTRAINT uq_cms_revision_number UNIQUE (tenant_id,content_id,revision_number),
  CONSTRAINT fk_cms_revision_content FOREIGN KEY (tenant_id,content_id) REFERENCES cms_content(tenant_id,id),
  CONSTRAINT fk_cms_revision_created_by FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id),
  INDEX ix_cms_revision_content (tenant_id,content_id,revision_number)
);

ALTER TABLE cms_content
  ADD CONSTRAINT fk_cms_content_current_revision
    FOREIGN KEY (tenant_id,current_revision_id) REFERENCES cms_content_revision(tenant_id,id),
  ADD CONSTRAINT fk_cms_content_published_revision
    FOREIGN KEY (tenant_id,published_revision_id) REFERENCES cms_content_revision(tenant_id,id);


-- Revision snapshots are immutable even if an application path is accidentally added later.
CREATE TRIGGER trg_cms_revision_no_update
BEFORE UPDATE ON cms_content_revision
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CMS content revisions are immutable';

CREATE TRIGGER trg_cms_revision_no_delete
BEFORE DELETE ON cms_content_revision
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CMS content revisions are immutable';
