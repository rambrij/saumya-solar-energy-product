-- Phase 2: enforce tenant-aware relationships for operational, financial and evidence records.
-- This migration is deliberately fail-closed. Repair any reported rows before retrying.
DROP PROCEDURE IF EXISTS assert_phase2_operational_tenant_integrity;
DELIMITER $
CREATE PROCEDURE assert_phase2_operational_tenant_integrity()
BEGIN
    -- Legacy tenant columns were nullable in the original Release 2 DDL.
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

-- Add composite referenced keys. Existing global primary keys remain unchanged.
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE enquiry ADD CONSTRAINT uq_enquiry_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE sales_lead ADD CONSTRAINT uq_sales_lead_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE site_survey ADD CONSTRAINT uq_site_survey_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE quotation ADD CONSTRAINT uq_quotation_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE customer_order ADD CONSTRAINT uq_customer_order_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE warehouse ADD CONSTRAINT uq_warehouse_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE installation_team ADD CONSTRAINT uq_installation_team_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE installation_job ADD CONSTRAINT uq_installation_job_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE invoice ADD CONSTRAINT uq_invoice_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE payment ADD CONSTRAINT uq_payment_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE service_request ADD CONSTRAINT uq_service_request_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE warranty ADD CONSTRAINT uq_warranty_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE warranty_claim ADD CONSTRAINT uq_warranty_claim_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE offline_sync_operation ADD CONSTRAINT uq_offline_sync_operation_tenant_id UNIQUE (tenant_id,id);

-- Sales, survey and quotation graph.
ALTER TABLE sales_lead
    ADD CONSTRAINT fk_sales_lead_enquiry_tenant FOREIGN KEY (tenant_id,enquiry_id) REFERENCES enquiry(tenant_id,id),
    ADD CONSTRAINT fk_sales_lead_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_sales_lead_assignee_tenant FOREIGN KEY (tenant_id,assigned_user_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE lead_history
    ADD CONSTRAINT fk_lead_history_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
    ADD CONSTRAINT fk_lead_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE site_survey
    ADD CONSTRAINT fk_site_survey_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
    ADD CONSTRAINT fk_site_survey_assignee_tenant FOREIGN KEY (tenant_id,assigned_user_id) REFERENCES app_user(tenant_id,id),
    ADD CONSTRAINT fk_site_survey_reviewer_tenant FOREIGN KEY (tenant_id,reviewed_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE quotation
    ADD CONSTRAINT fk_quotation_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_quotation_lead_tenant FOREIGN KEY (tenant_id,lead_id) REFERENCES sales_lead(tenant_id,id),
    ADD CONSTRAINT fk_quotation_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
    ADD CONSTRAINT fk_quotation_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id),
    ADD CONSTRAINT fk_quotation_decision_by_tenant FOREIGN KEY (tenant_id,decision_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE quotation_item
    ADD CONSTRAINT fk_quotation_item_quote_tenant FOREIGN KEY (tenant_id,quotation_id) REFERENCES quotation(tenant_id,id),
    ADD CONSTRAINT fk_quotation_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
    ADD CONSTRAINT fk_quotation_item_package_tenant FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id);

-- Orders, inventory, installation and warranty graph.
ALTER TABLE customer_order
    ADD CONSTRAINT fk_customer_order_quote_tenant FOREIGN KEY (tenant_id,quotation_id) REFERENCES quotation(tenant_id,id),
    ADD CONSTRAINT fk_customer_order_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id);
ALTER TABLE order_item
    ADD CONSTRAINT fk_order_item_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_order_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
    ADD CONSTRAINT fk_order_item_package_tenant FOREIGN KEY (tenant_id,package_id) REFERENCES solar_package(tenant_id,id);
ALTER TABLE order_status_history
    ADD CONSTRAINT fk_order_history_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_order_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE inventory_balance
    ADD CONSTRAINT fk_inventory_balance_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
    ADD CONSTRAINT fk_inventory_balance_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);
ALTER TABLE inventory_transaction
    ADD CONSTRAINT fk_inventory_tx_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_service_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_inventory_tx_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE inventory_reservation
    ADD CONSTRAINT fk_inventory_reservation_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_inventory_reservation_warehouse_tenant FOREIGN KEY (tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id),
    ADD CONSTRAINT fk_inventory_reservation_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);
ALTER TABLE installation_job
    ADD CONSTRAINT fk_install_job_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_install_job_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_install_job_engineer_tenant FOREIGN KEY (tenant_id,assigned_engineer_id) REFERENCES app_user(tenant_id,id),
    ADD CONSTRAINT fk_install_job_team_tenant FOREIGN KEY (tenant_id,team_id) REFERENCES installation_team(tenant_id,id);
ALTER TABLE installation_checklist_item
    ADD CONSTRAINT fk_checklist_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_checklist_completed_by_tenant FOREIGN KEY (tenant_id,completed_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE warranty
    ADD CONSTRAINT fk_warranty_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_warranty_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_warranty_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_warranty_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);

-- Finance, service and private evidence graph.
ALTER TABLE invoice
    ADD CONSTRAINT fk_invoice_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_invoice_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE invoice_item
    ADD CONSTRAINT fk_invoice_item_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
    ADD CONSTRAINT fk_invoice_item_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id);
ALTER TABLE payment
    ADD CONSTRAINT fk_payment_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
    ADD CONSTRAINT fk_payment_order_tenant FOREIGN KEY (tenant_id,order_id) REFERENCES customer_order(tenant_id,id),
    ADD CONSTRAINT fk_payment_recorded_by_tenant FOREIGN KEY (tenant_id,recorded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE payment_refund
    ADD CONSTRAINT fk_refund_payment_tenant FOREIGN KEY (tenant_id,payment_id) REFERENCES payment(tenant_id,id),
    ADD CONSTRAINT fk_refund_recorded_by_tenant FOREIGN KEY (tenant_id,recorded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE credit_note
    ADD CONSTRAINT fk_credit_note_invoice_tenant FOREIGN KEY (tenant_id,invoice_id) REFERENCES invoice(tenant_id,id),
    ADD CONSTRAINT fk_credit_note_created_by_tenant FOREIGN KEY (tenant_id,created_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_request
    ADD CONSTRAINT fk_service_request_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_service_request_installation_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_service_request_product_tenant FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id),
    ADD CONSTRAINT fk_service_request_engineer_tenant FOREIGN KEY (tenant_id,assigned_engineer_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_visit
    ADD CONSTRAINT fk_service_visit_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_service_visit_engineer_tenant FOREIGN KEY (tenant_id,engineer_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_event
    ADD CONSTRAINT fk_service_event_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_service_event_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_attachment
    ADD CONSTRAINT fk_service_attachment_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_service_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE service_comment
    ADD CONSTRAINT fk_service_comment_ticket_tenant FOREIGN KEY (tenant_id,service_request_id) REFERENCES service_request(tenant_id,id),
    ADD CONSTRAINT fk_service_comment_author_tenant FOREIGN KEY (tenant_id,author_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE site_survey_history
    ADD CONSTRAINT fk_survey_history_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
    ADD CONSTRAINT fk_survey_history_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE site_survey_attachment
    ADD CONSTRAINT fk_survey_attachment_survey_tenant FOREIGN KEY (tenant_id,survey_id) REFERENCES site_survey(tenant_id,id),
    ADD CONSTRAINT fk_survey_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE warranty_claim
    ADD CONSTRAINT fk_warranty_claim_warranty_tenant FOREIGN KEY (tenant_id,warranty_id) REFERENCES warranty(tenant_id,id),
    ADD CONSTRAINT fk_warranty_claim_customer_tenant FOREIGN KEY (tenant_id,customer_id) REFERENCES customer_profile(tenant_id,id),
    ADD CONSTRAINT fk_warranty_claim_reviewer_tenant FOREIGN KEY (tenant_id,reviewed_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE warranty_claim_attachment
    ADD CONSTRAINT fk_warranty_attachment_claim_tenant FOREIGN KEY (tenant_id,claim_id) REFERENCES warranty_claim(tenant_id,id),
    ADD CONSTRAINT fk_warranty_attachment_uploader_tenant FOREIGN KEY (tenant_id,uploaded_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE installation_schedule_history
    ADD CONSTRAINT fk_install_schedule_job_tenant FOREIGN KEY (tenant_id,installation_id) REFERENCES installation_job(tenant_id,id),
    ADD CONSTRAINT fk_install_schedule_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE offline_sync_operation
    ADD CONSTRAINT fk_offline_sync_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE offline_sync_attachment
    ADD CONSTRAINT fk_offline_attachment_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id),
    ADD CONSTRAINT fk_offline_attachment_operation_tenant FOREIGN KEY (tenant_id,operation_id) REFERENCES offline_sync_operation(tenant_id,id);
ALTER TABLE integration_secret_reference
    ADD CONSTRAINT fk_secret_reference_connection_tenant FOREIGN KEY (tenant_id,integration_connection_id) REFERENCES integration_connection(tenant_id,id);
ALTER TABLE branch_user
    ADD CONSTRAINT fk_branch_user_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE engineer_profile
    ADD CONSTRAINT fk_engineer_profile_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE approval_instance
    ADD CONSTRAINT fk_approval_instance_requester_tenant FOREIGN KEY (tenant_id,requested_by) REFERENCES app_user(tenant_id,id);
ALTER TABLE approval_decision
    ADD CONSTRAINT fk_approval_decision_actor_tenant FOREIGN KEY (tenant_id,actor_id) REFERENCES app_user(tenant_id,id);
ALTER TABLE audit_event
    ADD CONSTRAINT fk_audit_event_branch_tenant FOREIGN KEY (tenant_id,branch_id) REFERENCES branch(tenant_id,id);
ALTER TABLE notification_delivery
    ADD CONSTRAINT fk_notification_delivery_user_tenant FOREIGN KEY (tenant_id,user_id) REFERENCES app_user(tenant_id,id);

-- Supporting lookup indexes for the most frequently traversed tenant relationships.
CREATE INDEX ix_sales_lead_tenant_enquiry ON sales_lead(tenant_id,enquiry_id);
CREATE INDEX ix_quotation_tenant_customer ON quotation(tenant_id,customer_id);
CREATE INDEX ix_customer_order_tenant_customer ON customer_order(tenant_id,customer_id);
CREATE INDEX ix_invoice_item_tenant_product ON invoice_item(tenant_id,product_id);
CREATE INDEX ix_service_request_tenant_customer ON service_request(tenant_id,customer_id);
CREATE INDEX ix_warranty_claim_tenant_customer ON warranty_claim(tenant_id,customer_id);

-- Rollback is a new forward migration only; never edit V58 after it has been applied.
-- To roll back manually, drop all fk_*_tenant constraints added above (including
-- fk_engineer_profile_user_tenant, fk_approval_instance_requester_tenant and
-- fk_approval_decision_actor_tenant), then uq_*_tenant_id
-- unique indexes. Do not drop pre-existing single-column or V37/V43/V49/V57 constraints.
