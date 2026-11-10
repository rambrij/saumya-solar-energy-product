-- MySQL 8.4 integration assertions for V57 composite tenant FKs.
DELIMITER $$
CREATE PROCEDURE assert_v57_rejects_cross_tenant_catalogue_links()
BEGIN
    DECLARE tenant_a BINARY(16);
    DECLARE tenant_b BINARY(16);
    DECLARE category_a BINARY(16);
    DECLARE category_b BINARY(16);
    DECLARE product_a BINARY(16);
    DECLARE product_b BINARY(16);
    DECLARE package_a BINARY(16);
    DECLARE package_b BINARY(16);
    DECLARE rejected BOOLEAN DEFAULT FALSE;

    SET tenant_a=UUID_TO_BIN(UUID());
    SET tenant_b=UUID_TO_BIN(UUID());
    SET category_a=UUID_TO_BIN(UUID());
    SET category_b=UUID_TO_BIN(UUID());
    SET product_a=UUID_TO_BIN(UUID());
    SET product_b=UUID_TO_BIN(UUID());
    SET package_a=UUID_TO_BIN(UUID());
    SET package_b=UUID_TO_BIN(UUID());

    INSERT INTO tenant(id,name,slug,status) VALUES
        (tenant_a,'V57 Tenant A',CONCAT('v57-a-',REPLACE(UUID(),'-','')),'ACTIVE'),
        (tenant_b,'V57 Tenant B',CONCAT('v57-b-',REPLACE(UUID(),'-','')),'ACTIVE');
    INSERT INTO product_category(id,tenant_id,name,active) VALUES
        (category_a,tenant_a,'Tenant A category',TRUE),
        (category_b,tenant_b,'Tenant B category',TRUE);
    INSERT INTO product(id,tenant_id,category_id,sku,name,price,tax_percent,active) VALUES
        (product_a,tenant_a,category_a,CONCAT('V57-A-',REPLACE(UUID(),'-','')),'Tenant A product',1,0,TRUE),
        (product_b,tenant_b,category_b,CONCAT('V57-B-',REPLACE(UUID(),'-','')),'Tenant B product',1,0,TRUE);
    INSERT INTO solar_package(id,tenant_id,code,name,active) VALUES
        (package_a,tenant_a,CONCAT('V57-A-',REPLACE(UUID(),'-','')),'Tenant A package',TRUE),
        (package_b,tenant_b,CONCAT('V57-B-',REPLACE(UUID(),'-','')),'Tenant B package',TRUE);

    INSERT INTO package_item(id,tenant_id,package_id,product_id,quantity,optional_item)
        VALUES(UUID_TO_BIN(UUID()),tenant_a,package_a,product_a,1,FALSE);

    SET rejected=FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected=TRUE;
        INSERT INTO product(id,tenant_id,category_id,sku,name,price,tax_percent,active)
            VALUES(UUID_TO_BIN(UUID()),tenant_a,category_b,CONCAT('V57-X-',REPLACE(UUID(),'-','')),'Cross tenant category',1,0,TRUE);
    END;
    IF rejected=FALSE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 failed to reject cross-tenant product category';
    END IF;

    SET rejected=FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected=TRUE;
        INSERT INTO package_item(id,tenant_id,package_id,product_id,quantity,optional_item)
            VALUES(UUID_TO_BIN(UUID()),tenant_a,package_b,product_a,1,FALSE);
    END;
    IF rejected=FALSE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 failed to reject cross-tenant package reference';
    END IF;

    SET rejected=FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected=TRUE;
        INSERT INTO package_item(id,tenant_id,package_id,product_id,quantity,optional_item)
            VALUES(UUID_TO_BIN(UUID()),tenant_a,package_a,product_b,1,FALSE);
    END;
    IF rejected=FALSE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V57 failed to reject cross-tenant product reference';
    END IF;
END$$
DELIMITER ;

CALL assert_v57_rejects_cross_tenant_catalogue_links();
DROP PROCEDURE assert_v57_rejects_cross_tenant_catalogue_links;
