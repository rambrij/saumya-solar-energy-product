-- Phase 2: enforce tenant ownership for core catalogue relationships.
-- Preflight is deliberately fail-closed: do not add constraints over dirty legacy data.
DROP PROCEDURE IF EXISTS assert_phase2_catalogue_tenant_integrity;
DELIMITER $
CREATE PROCEDURE assert_phase2_catalogue_tenant_integrity()
BEGIN
    IF EXISTS (
        SELECT 1 FROM product p
        LEFT JOIN product_category c ON c.id=p.category_id AND c.tenant_id=p.tenant_id
        WHERE p.category_id IS NOT NULL AND c.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 blocked: product.category_id references a missing or cross-tenant category';
    END IF;

    IF EXISTS (
        SELECT 1 FROM package_item i
        LEFT JOIN solar_package p ON p.id=i.package_id AND p.tenant_id=i.tenant_id
        WHERE i.package_id IS NOT NULL AND (i.tenant_id IS NULL OR p.id IS NULL)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 blocked: package_item.package_id is orphaned or cross-tenant';
    END IF;

    IF EXISTS (
        SELECT 1 FROM package_item i
        LEFT JOIN product p ON p.id=i.product_id AND p.tenant_id=i.tenant_id
        WHERE i.product_id IS NOT NULL AND (i.tenant_id IS NULL OR p.id IS NULL)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 blocked: package_item.product_id is orphaned or cross-tenant';
    END IF;

    IF EXISTS (SELECT 1 FROM package_item WHERE tenant_id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 blocked: package_item contains rows without tenant_id';
    END IF;
END$
DELIMITER ;
CALL assert_phase2_catalogue_tenant_integrity();
DROP PROCEDURE assert_phase2_catalogue_tenant_integrity;

-- Composite referenced keys are required by MySQL for tenant-aware foreign keys.
ALTER TABLE product_category
    ADD CONSTRAINT uq_product_category_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE product
    ADD CONSTRAINT uq_product_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE solar_package
    ADD CONSTRAINT uq_solar_package_tenant_id UNIQUE (tenant_id,id);

ALTER TABLE product
    ADD CONSTRAINT fk_product_category_tenant
    FOREIGN KEY (tenant_id,category_id) REFERENCES product_category(tenant_id,id);

ALTER TABLE package_item
    MODIFY tenant_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_package_item_package_tenant
        FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id),
    ADD CONSTRAINT fk_package_item_product_tenant
        FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);

-- Rollback (manual, forward-only Flyway deployment):
-- ALTER TABLE package_item DROP FOREIGN KEY fk_package_item_product_tenant,
--     DROP FOREIGN KEY fk_package_item_package_tenant;
-- ALTER TABLE product DROP FOREIGN KEY fk_product_category_tenant;
-- ALTER TABLE solar_package DROP INDEX uq_solar_package_tenant_id;
-- ALTER TABLE product DROP INDEX uq_product_tenant_id;
-- ALTER TABLE product_category DROP INDEX uq_product_category_tenant_id;
-- ALTER TABLE package_item MODIFY tenant_id BINARY(16) NULL;
