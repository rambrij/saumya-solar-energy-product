-- MySQL 8.4 negative assertions for Phase 2 V58 composite tenant FKs.
-- Executed after V58 in release-validation.yml using an isolated migration database.
DROP PROCEDURE IF EXISTS assert_v58_rejects_cross_tenant_operational_links;
DELIMITER $
CREATE PROCEDURE assert_v58_rejects_cross_tenant_operational_links()
BEGIN
    DECLARE tenant_a BINARY(16);
    DECLARE tenant_b BINARY(16);
    DECLARE user_a BINARY(16);
    DECLARE user_b BINARY(16);
    DECLARE customer_a BINARY(16);
    DECLARE customer_b BINARY(16);
    DECLARE lead_a BINARY(16);
    DECLARE lead_b BINARY(16);
    DECLARE ticket_a BINARY(16);
    DECLARE ticket_b BINARY(16);
    DECLARE rejected BOOLEAN DEFAULT FALSE;

    SET tenant_a=UUID_TO_BIN(UUID());
    SET tenant_b=UUID_TO_BIN(UUID());
    SET user_a=UUID_TO_BIN(UUID());
    SET user_b=UUID_TO_BIN(UUID());
    SET customer_a=UUID_TO_BIN(UUID());
    SET customer_b=UUID_TO_BIN(UUID());
    SET lead_a=UUID_TO_BIN(UUID());
    SET lead_b=UUID_TO_BIN(UUID());
    SET ticket_a=UUID_TO_BIN(UUID());
    SET ticket_b=UUID_TO_BIN(UUID());

    INSERT INTO tenant(id,name,slug,status) VALUES
      (tenant_a,'V58 Tenant A',CONCAT('v58-a-',REPLACE(UUID(),'-','')),'ACTIVE'),
      (tenant_b,'V58 Tenant B',CONCAT('v58-b-',REPLACE(UUID(),'-','')),'ACTIVE');

    INSERT INTO app_user(id,tenant_id,name,email,password_hash,account_type,enabled) VALUES
      (user_a,tenant_a,'V58 Actor A',CONCAT('v58-a-',REPLACE(UUID(),'-',''),'@example.test'),'not-used','VENDOR',TRUE),
      (user_b,tenant_b,'V58 Actor B',CONCAT('v58-b-',REPLACE(UUID(),'-',''),'@example.test'),'not-used','VENDOR',TRUE);

    INSERT INTO customer_profile(id,tenant_id,name,email,active) VALUES
      (customer_a,tenant_a,'V58 Customer A',CONCAT('v58-a-',REPLACE(UUID(),'-',''),'@example.test'),TRUE),
      (customer_b,tenant_b,'V58 Customer B',CONCAT('v58-b-',REPLACE(UUID(),'-',''),'@example.test'),TRUE);

    SET rejected=FALSE;
    BEGIN
      DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected=TRUE;
      INSERT INTO customer_profile(id,tenant_id,user_id,name,email,active)
        VALUES(UUID_TO_BIN(UUID()),tenant_a,user_b,'Cross tenant profile',CONCAT('v58-cross-',REPLACE(UUID(),'-',''),'@example.test'),TRUE);
    END;
    IF rejected=FALSE THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 failed to reject cross-tenant customer_profile.user_id';
    END IF;

    INSERT INTO sales_lead(id,tenant_id,stage,priority,created_at) VALUES
      (lead_a,tenant_a,'NEW','LOW',CURRENT_TIMESTAMP(6)),
      (lead_b,tenant_b,'NEW','LOW',CURRENT_TIMESTAMP(6));

    SET rejected=FALSE;
    BEGIN
      DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected=TRUE;
      INSERT INTO lead_history(id,tenant_id,lead_id,actor_id,created_at)
        VALUES(UUID_TO_BIN(UUID()),tenant_a,lead_b,user_a,CURRENT_TIMESTAMP(6));
    END;
    IF rejected=FALSE THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 failed to reject cross-tenant lead_history.lead_id';
    END IF;

    INSERT INTO service_request(id,tenant_id,ticket_number,customer_id,category,priority,description,warranty_status,status,created_at,updated_at) VALUES
      (ticket_a,tenant_a,CONCAT('V58-A-',REPLACE(UUID(),'-','')),customer_a,'GENERAL','LOW','Tenant A ticket','UNKNOWN','OPEN',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
      (ticket_b,tenant_b,CONCAT('V58-B-',REPLACE(UUID(),'-','')),customer_b,'GENERAL','LOW','Tenant B ticket','UNKNOWN','OPEN',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));

    SET rejected=FALSE;
    BEGIN
      DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected=TRUE;
      INSERT INTO service_comment(id,tenant_id,service_request_id,author_id,visibility,body,created_at)
        VALUES(UUID_TO_BIN(UUID()),tenant_a,ticket_b,user_a,'INTERNAL','Cross-tenant comment',CURRENT_TIMESTAMP(6));
    END;
    IF rejected=FALSE THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 failed to reject cross-tenant service_comment.service_request_id';
    END IF;

    SET rejected=FALSE;
    BEGIN
      DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET rejected=TRUE;
      INSERT INTO service_request(id,tenant_id,ticket_number,customer_id,category,priority,description,warranty_status,status,created_at,updated_at)
        VALUES(UUID_TO_BIN(UUID()),tenant_a,CONCAT('V58-X-',REPLACE(UUID(),'-','')),customer_b,'GENERAL','LOW','Cross-tenant customer link','UNKNOWN','OPEN',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));
    END;
    IF rejected=FALSE THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 failed to reject cross-tenant service_request.customer_id';
    END IF;
END$
DELIMITER ;

CALL assert_v58_rejects_cross_tenant_operational_links();
DROP PROCEDURE assert_v58_rejects_cross_tenant_operational_links;
