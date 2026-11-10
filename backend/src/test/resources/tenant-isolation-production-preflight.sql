-- READ-ONLY DATA PREFLIGHT for production rollout of Flyway V57/V58.
-- This creates and drops temporary stored procedures only. It does not alter business rows,
-- add indexes, or apply the V57/V58 constraints. A SIGNAL aborts immediately on dirty data.
-- Run with mysql client; requires CREATE ROUTINE and DROP ROUTINE privileges.
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


SELECT 'V57 catalogue tenant-integrity preflight: PASS' AS result;
-- Phase 2: enforce tenant-aware relationships for operational, financial and evidence records.
-- This migration is deliberately fail-closed. Repair any reported rows before retrying.
DROP PROCEDURE IF EXISTS assert_phase2_operational_tenant_integrity;
DELIMITER $
CREATE PROCEDURE assert_phase2_operational_tenant_integrity()
BEGIN
    -- Legacy tenant columns were nullable in the original Release 2 DDL.
    IF EXISTS (SELECT 1 FROM customer_profile c LEFT JOIN app_user u ON u.tenant_id=c.tenant_id AND u.id=c.user_id WHERE c.user_id IS NOT NULL AND u.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: customer profile user is missing or belongs to another tenant';
    END IF;

    IF EXISTS (SELECT 1 FROM lead_history WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation_item WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM customer_order WHERE tenant_id IS NULL)
       OR EXISTS (SELECT 1 FROM order_item WHERE tenant_id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: legacy tenant-owned sales rows have NULL tenant_id';
    END IF;

    -- Sales, survey and quotation graph.
    IF EXISTS (SELECT 1 FROM sales_lead c LEFT JOIN enquiry p ON p.tenant_id=c.tenant_id AND p.id=c.enquiry_id WHERE c.enquiry_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM sales_lead c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE c.customer_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM sales_lead c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.assigned_user_id WHERE c.assigned_user_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM lead_history c LEFT JOIN sales_lead p ON p.tenant_id=c.tenant_id AND p.id=c.lead_id WHERE c.lead_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM lead_history c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE c.actor_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey c LEFT JOIN sales_lead p ON p.tenant_id=c.tenant_id AND p.id=c.lead_id WHERE c.lead_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.assigned_user_id WHERE c.assigned_user_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.reviewed_by WHERE c.reviewed_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE c.customer_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN sales_lead p ON p.tenant_id=c.tenant_id AND p.id=c.lead_id WHERE c.lead_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN site_survey p ON p.tenant_id=c.tenant_id AND p.id=c.survey_id WHERE c.survey_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.created_by WHERE c.created_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.decision_by WHERE c.decision_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation_item c LEFT JOIN quotation p ON p.tenant_id=c.tenant_id AND p.id=c.quotation_id WHERE c.quotation_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation_item c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.product_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM quotation_item c LEFT JOIN solar_package p ON p.tenant_id=c.tenant_id AND p.id=c.package_id WHERE c.package_id IS NOT NULL AND p.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: sales, survey or quotation relationship is orphaned or cross-tenant';
    END IF;

    -- Orders, inventory, installation and warranty graph.
    IF EXISTS (SELECT 1 FROM customer_order c LEFT JOIN quotation p ON p.tenant_id=c.tenant_id AND p.id=c.quotation_id WHERE c.quotation_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM customer_order c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE c.customer_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_item c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE c.order_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_item c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.product_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_item c LEFT JOIN solar_package p ON p.tenant_id=c.tenant_id AND p.id=c.package_id WHERE c.package_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_status_history c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM order_status_history c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_balance c LEFT JOIN warehouse p ON p.tenant_id=c.tenant_id AND p.id=c.warehouse_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_balance c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN warehouse p ON p.tenant_id=c.tenant_id AND p.id=c.warehouse_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE c.order_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE c.installation_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE c.service_request_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_transaction c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_reservation c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_reservation c LEFT JOIN warehouse p ON p.tenant_id=c.tenant_id AND p.id=c.warehouse_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM inventory_reservation c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_job c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_job c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_job c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.assigned_engineer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_job c LEFT JOIN installation_team p ON p.tenant_id=c.tenant_id AND p.id=c.team_id WHERE c.team_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_checklist_item c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_checklist_item c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.completed_by WHERE c.completed_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE p.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: order, inventory, installation or warranty relationship is orphaned or cross-tenant';
    END IF;

    -- Finance, service and private evidence graph.
    IF EXISTS (SELECT 1 FROM invoice c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM invoice c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.created_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM invoice_item c LEFT JOIN invoice p ON p.tenant_id=c.tenant_id AND p.id=c.invoice_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM invoice_item c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.product_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment c LEFT JOIN invoice p ON p.tenant_id=c.tenant_id AND p.id=c.invoice_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment c LEFT JOIN customer_order p ON p.tenant_id=c.tenant_id AND p.id=c.order_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.recorded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment_refund c LEFT JOIN payment p ON p.tenant_id=c.tenant_id AND p.id=c.payment_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM payment_refund c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.recorded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM credit_note c LEFT JOIN invoice p ON p.tenant_id=c.tenant_id AND p.id=c.invoice_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM credit_note c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.created_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_request c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_request c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE c.installation_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_request c LEFT JOIN product p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.product_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_request c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.assigned_engineer_id WHERE c.assigned_engineer_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_visit c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_visit c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.engineer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_event c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_event c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE c.actor_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_attachment c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_attachment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.uploaded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_comment c LEFT JOIN service_request p ON p.tenant_id=c.tenant_id AND p.id=c.service_request_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM service_comment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.author_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey_history c LEFT JOIN site_survey p ON p.tenant_id=c.tenant_id AND p.id=c.survey_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey_history c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey_attachment c LEFT JOIN site_survey p ON p.tenant_id=c.tenant_id AND p.id=c.survey_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM site_survey_attachment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.uploaded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim c LEFT JOIN warranty p ON p.tenant_id=c.tenant_id AND p.id=c.warranty_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim c LEFT JOIN customer_profile p ON p.tenant_id=c.tenant_id AND p.id=c.customer_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.reviewed_by WHERE c.reviewed_by IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim_attachment c LEFT JOIN warranty_claim p ON p.tenant_id=c.tenant_id AND p.id=c.claim_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM warranty_claim_attachment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.uploaded_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_schedule_history c LEFT JOIN installation_job p ON p.tenant_id=c.tenant_id AND p.id=c.installation_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM installation_schedule_history c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM offline_sync_operation c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM offline_sync_attachment c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM offline_sync_attachment c LEFT JOIN offline_sync_operation p ON p.tenant_id=c.tenant_id AND p.id=c.operation_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM integration_secret_reference c LEFT JOIN integration_connection p ON p.tenant_id=c.tenant_id AND p.id=c.integration_connection_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM branch_user c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.user_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM engineer_profile c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.user_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM approval_instance c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.requested_by WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM approval_decision c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.actor_id WHERE p.id IS NULL)
       OR EXISTS (SELECT 1 FROM audit_event c LEFT JOIN branch p ON p.tenant_id=c.tenant_id AND p.id=c.branch_id WHERE c.branch_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM notification_delivery c LEFT JOIN app_user p ON p.tenant_id=c.tenant_id AND p.id=c.user_id WHERE c.tenant_id IS NOT NULL AND c.user_id IS NOT NULL AND p.id IS NULL)
       OR EXISTS (SELECT 1 FROM notification_delivery c JOIN app_user u ON u.id=c.user_id WHERE c.tenant_id IS NULL AND u.tenant_id IS NOT NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V58 blocked: finance, service, evidence, identity or integration relationship is orphaned or cross-tenant';
    END IF;
END$
DELIMITER ;
CALL assert_phase2_operational_tenant_integrity();
DROP PROCEDURE assert_phase2_operational_tenant_integrity;


SELECT 'V58 operational tenant-integrity preflight: PASS' AS result;
