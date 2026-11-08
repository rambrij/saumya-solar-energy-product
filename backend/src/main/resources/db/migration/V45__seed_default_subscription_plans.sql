-- Idempotent baseline subscription catalogue. Existing tenant assignments and plan history are untouched.
INSERT INTO subscription_plan
(id,plan_code,name,description,billing_cycle,price,currency,max_users,max_branches,max_warehouses,max_storage_mb,entitlements_json,active,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()),src.plan_code,src.name,src.description,src.billing_cycle,src.price,'INR',
       src.max_users,src.max_branches,src.max_warehouses,src.max_storage_mb,src.entitlements_json,TRUE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
FROM (
 SELECT 'STARTER' plan_code,'Starter' name,'For small solar businesses starting digital operations.' description,
        'MONTHLY' billing_cycle,999.00 price,5 max_users,1 max_branches,1 max_warehouses,512 max_storage_mb,
        '{"branches":false,"multiWarehouse":false,"customBranding":false,"approvalWorkflows":false}' entitlements_json
 UNION ALL
 SELECT 'PROFESSIONAL','Professional','For growing solar businesses managing sales and field operations.',
        'MONTHLY',2999.00,25,3,3,5120,
        '{"branches":true,"multiWarehouse":true,"customBranding":true,"approvalWorkflows":true,"accountingIntegration":false}'
 UNION ALL
 SELECT 'ENTERPRISE','Enterprise','For multi-location solar businesses with advanced operational needs.',
        'MONTHLY',9999.00,100,20,20,51200,
        '{"branches":true,"multiWarehouse":true,"customBranding":true,"customDomains":true,"approvalWorkflows":true,"accountingIntegration":true,"paymentGateway":true,"apiIntegrations":true}'
) src
LEFT JOIN subscription_plan existing ON existing.plan_code=src.plan_code
WHERE existing.id IS NULL;
