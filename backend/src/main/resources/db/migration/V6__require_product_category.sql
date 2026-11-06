UPDATE product p
JOIN (SELECT tenant_id, MIN(id) category_id FROM product_category GROUP BY tenant_id) c
ON c.tenant_id=p.tenant_id
SET p.category_id=c.category_id
WHERE p.category_id IS NULL;

ALTER TABLE product MODIFY COLUMN category_id BINARY(16) NOT NULL;
