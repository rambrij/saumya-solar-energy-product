package com.saumyasolar.service;

import com.saumyasolar.domain.Branch;
import com.saumyasolar.repo.BranchRepository;
import com.saumyasolar.security.Principal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.*;

@Service
public class VendorAnalyticsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final BranchRepository branches;
    
    public VendorAnalyticsService(NamedParameterJdbcTemplate jdbc, BranchRepository branches) {
        this.jdbc = jdbc;
        this.branches = branches;
    }
    
    private MapSqlParameterSource scope(
            Principal actor,
            Instant from,
            Instant to,
            UUID branchId,
            UUID categoryId,
            UUID productId
    ) {
        if (actor == null || !"VENDOR".equals(actor.accountType()) || actor.tenantId() == null || actor.roles() == null || !actor.roles().contains("VENDOR_ADMIN")
                || actor.permissions() == null || !actor.permissions().contains("REPORT_VIEW") || !actor.permissions().contains("BRANCH_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendor Admin analytics access required");
        Instant end = to == null ? Instant.now() : to, start = from == null ? end.minus(Duration.ofDays(90)) : from;
        if (end.isBefore(start) || Duration.between(start, end).toDays() > 366)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analytics date range must be valid and no longer than 366 days");
        if (branchId != null)
            branches.findByIdAndTenantId(branchId, actor.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
        return new MapSqlParameterSource().addValue("tenantId", actor.tenantId()).addValue("from", Timestamp.from(start)).addValue("to", Timestamp.from(end))
                .addValue("branchId", branchId).addValue("categoryId", categoryId).addValue("productId", productId);
    }
    
    public Map<String, Object> breakdowns(
            Principal actor,
            Instant from,
            Instant to,
            UUID branchId,
            UUID categoryId,
            UUID productId
    ) {
        MapSqlParameterSource p = scope(actor, from, to, branchId, categoryId, productId);
        String orderFilter = "o.tenant_id=:tenantId AND o.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR o.branch_id=:branchId) AND (:categoryId IS NULL OR p.category_id=:categoryId) AND (:productId IS NULL OR p.id=:productId)";
        String quoteFilter = "q.tenant_id=:tenantId AND q.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR q.branch_id=:branchId) AND (:categoryId IS NULL OR p.category_id=:categoryId) AND (:productId IS NULL OR p.id=:productId)";
        String categorySql = "SELECT c.id,c.name,COALESCE(ord.orders,0) orders,COALESCE(ord.revenue,0) revenue,COALESCE(crm.leads,0) leads,COALESCE(crm.enquiries,0) enquiries,COALESCE(crm.quoteValue,0) quoteValue FROM product_category c " +
                "LEFT JOIN (SELECT p.category_id,COUNT(DISTINCT o.id) orders,COALESCE(SUM(oi.line_total),0) revenue FROM product p JOIN order_item oi ON oi.product_id=p.id AND oi.tenant_id=p.tenant_id JOIN customer_order o ON o.id=oi.order_id AND o.tenant_id=oi.tenant_id WHERE p.tenant_id=:tenantId AND " + orderFilter + " GROUP BY p.category_id) ord ON ord.category_id=c.id " +
                "LEFT JOIN (SELECT p.category_id,COUNT(DISTINCT sl.id) leads,COUNT(DISTINCT e.id) enquiries,COALESCE(SUM(qi.line_total),0) quoteValue FROM product p JOIN quotation_item qi ON qi.product_id=p.id AND qi.tenant_id=p.tenant_id JOIN quotation q ON q.id=qi.quotation_id AND q.tenant_id=qi.tenant_id LEFT JOIN sales_lead sl ON sl.id=q.lead_id AND sl.tenant_id=q.tenant_id LEFT JOIN enquiry e ON e.id=sl.enquiry_id AND e.tenant_id=sl.tenant_id WHERE p.tenant_id=:tenantId AND " + quoteFilter + " GROUP BY p.category_id) crm ON crm.category_id=c.id " +
                "WHERE c.tenant_id=:tenantId AND c.active=TRUE AND (:categoryId IS NULL OR c.id=:categoryId) ORDER BY revenue DESC,c.name";
        String productSql = "SELECT p.id,p.name,p.category_id categoryId,COALESCE(ord.orders,0) orders,COALESCE(ord.revenue,0) revenue,COALESCE(crm.leads,0) leads,COALESCE(crm.enquiries,0) enquiries,COALESCE(crm.quoteValue,0) quoteValue FROM product p " +
                "LEFT JOIN (SELECT oi.product_id,COUNT(DISTINCT o.id) orders,COALESCE(SUM(oi.line_total),0) revenue FROM order_item oi JOIN customer_order o ON o.id=oi.order_id AND o.tenant_id=oi.tenant_id WHERE oi.tenant_id=:tenantId AND o.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR o.branch_id=:branchId) GROUP BY oi.product_id) ord ON ord.product_id=p.id " +
                "LEFT JOIN (SELECT qi.product_id,COUNT(DISTINCT sl.id) leads,COUNT(DISTINCT e.id) enquiries,COALESCE(SUM(qi.line_total),0) quoteValue FROM quotation_item qi JOIN quotation q ON q.id=qi.quotation_id AND q.tenant_id=qi.tenant_id LEFT JOIN sales_lead sl ON sl.id=q.lead_id AND sl.tenant_id=q.tenant_id LEFT JOIN enquiry e ON e.id=sl.enquiry_id AND e.tenant_id=sl.tenant_id WHERE qi.tenant_id=:tenantId AND q.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR q.branch_id=:branchId) GROUP BY qi.product_id) crm ON crm.product_id=p.id " +
                "WHERE p.tenant_id=:tenantId AND p.active=TRUE AND (:categoryId IS NULL OR p.category_id=:categoryId) AND (:productId IS NULL OR p.id=:productId) ORDER BY revenue DESC,p.name";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", jdbc.queryForList(categorySql, p));
        result.put("products", jdbc.queryForList(productSql, p));
        result.put("from", p.getValue("from"));
        result.put("to", p.getValue("to"));
        return result;
    }
    
    public Map<String, Object> trends(
            Principal actor,
            Instant from,
            Instant to,
            UUID branchId,
            UUID categoryId,
            UUID productId,
            String granularity
    ) {
        MapSqlParameterSource p = scope(actor, from, to, branchId, categoryId, productId);
        String format = switch (granularity == null ? "DAY" : granularity.toUpperCase(Locale.ROOT)) {
            case "WEEK" -> "%x-W%v";
            case "MONTH" -> "%Y-%m";
            case "DAY" -> "%Y-%m-%d";
            default ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Granularity must be day, week or month");
        };
        TreeMap<String, Map<String, Object>> series = new TreeMap<>();
        mergeTrend(series, "SELECT DATE_FORMAT(l.created_at,'" + format + "') period,COUNT(*) AS metric_value FROM sales_lead l WHERE l.tenant_id=:tenantId AND l.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR l.branch_id=:branchId) AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM quotation q JOIN quotation_item qi ON qi.quotation_id=q.id AND qi.tenant_id=q.tenant_id JOIN product p ON p.id=qi.product_id AND p.tenant_id=qi.tenant_id WHERE q.lead_id=l.id AND p.category_id=:categoryId)) AND (:productId IS NULL OR EXISTS (SELECT 1 FROM quotation q JOIN quotation_item qi ON qi.quotation_id=q.id AND qi.tenant_id=q.tenant_id WHERE q.lead_id=l.id AND qi.product_id=:productId)) GROUP BY period", "leads", p, false);
        mergeTrend(series, "SELECT DATE_FORMAT(e.created_at,'" + format + "') period,COUNT(*) AS metric_value FROM enquiry e WHERE e.tenant_id=:tenantId AND e.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR e.branch_id=:branchId) AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM sales_lead l JOIN quotation q ON q.lead_id=l.id AND q.tenant_id=l.tenant_id JOIN quotation_item qi ON qi.quotation_id=q.id AND qi.tenant_id=q.tenant_id JOIN product p ON p.id=qi.product_id AND p.tenant_id=qi.tenant_id WHERE l.tenant_id=e.tenant_id AND l.enquiry_id=e.id AND p.category_id=:categoryId)) AND (:productId IS NULL OR EXISTS (SELECT 1 FROM sales_lead l JOIN quotation q ON q.lead_id=l.id AND q.tenant_id=l.tenant_id JOIN quotation_item qi ON qi.quotation_id=q.id AND qi.tenant_id=q.tenant_id WHERE l.tenant_id=e.tenant_id AND l.enquiry_id=e.id AND qi.product_id=:productId)) GROUP BY period", "enquiries", p, false);
        mergeTrend(series, "SELECT DATE_FORMAT(o.created_at,'" + format + "') period,COUNT(*) AS metric_value FROM customer_order o WHERE o.tenant_id=:tenantId AND o.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR o.branch_id=:branchId) AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM order_item oi JOIN product pr ON pr.id=oi.product_id AND pr.tenant_id=oi.tenant_id WHERE oi.order_id=o.id AND oi.tenant_id=o.tenant_id AND pr.tenant_id=o.tenant_id AND pr.category_id=:categoryId)) AND (:productId IS NULL OR EXISTS (SELECT 1 FROM order_item oi WHERE oi.order_id=o.id AND oi.tenant_id=o.tenant_id AND oi.product_id=:productId)) GROUP BY period", "orders", p, false);
        mergeTrend(series, "SELECT DATE_FORMAT(o.created_at,'" + format + "') period,COALESCE(SUM(o.total),0) AS metric_value FROM customer_order o WHERE o.tenant_id=:tenantId AND o.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR o.branch_id=:branchId) AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM order_item oi JOIN product pr ON pr.id=oi.product_id WHERE oi.order_id=o.id AND pr.category_id=:categoryId)) AND (:productId IS NULL OR EXISTS (SELECT 1 FROM order_item oi WHERE oi.order_id=o.id AND oi.product_id=:productId)) GROUP BY period", "revenue", p, true);
        return Map.of("granularity", granularity == null ? "DAY" : granularity.toUpperCase(Locale.ROOT), "items", new ArrayList<>(series.values()));
    }
    
    public Map<String,Object> operations(Principal actor, Instant from, Instant to, UUID branchId, UUID employeeId) {
        MapSqlParameterSource p = scope(actor, from, to, branchId, null, null).addValue("employeeId", employeeId);
        String installationsSql = """
                SELECT ij.status, COUNT(*) itemCount FROM installation_job ij
                WHERE ij.tenant_id=:tenantId AND ij.scheduled_at BETWEEN :from AND :to
                  AND (:branchId IS NULL OR ij.branch_id=:branchId)
                  AND (:employeeId IS NULL OR ij.assigned_engineer_id=:employeeId)
                GROUP BY ij.status ORDER BY ij.status
                """;
        String servicesSql = """
                SELECT sr.status, COUNT(*) itemCount FROM service_request sr
                WHERE sr.tenant_id=:tenantId AND sr.created_at BETWEEN :from AND :to
                  AND (:branchId IS NULL OR sr.branch_id=:branchId)
                  AND (:employeeId IS NULL OR sr.assigned_engineer_id=:employeeId)
                GROUP BY sr.status ORDER BY sr.status
                """;
        String stockSql = """
                SELECT pr.id,pr.sku,pr.name,pr.reorder_level reorderLevel,
                       COALESCE(SUM(ib.on_hand-ib.reserved-ib.damaged),0) available
                FROM product pr
                LEFT JOIN inventory_balance ib ON ib.tenant_id=pr.tenant_id AND ib.product_id=pr.id
                LEFT JOIN warehouse wh ON wh.tenant_id=ib.tenant_id AND wh.id=ib.warehouse_id
                WHERE pr.tenant_id=:tenantId AND pr.active=TRUE AND (:branchId IS NULL OR wh.branch_id=:branchId)
                GROUP BY pr.id,pr.sku,pr.name,pr.reorder_level
                HAVING available <= pr.reorder_level ORDER BY available ASC,pr.name LIMIT 100
                """;
        String employeeSql = """
                SELECT u.id userId,u.name,
                       COUNT(DISTINCT ij.id) installationCount,
                       COUNT(DISTINCT CASE WHEN ij.status IN ('COMPLETED','COMMISSIONED') THEN ij.id END) completedInstallations,
                       COUNT(DISTINCT sr.id) serviceTicketCount,
                       COUNT(DISTINCT CASE WHEN sr.status IN ('RESOLVED','CUSTOMER_CONFIRMED','CLOSED') THEN sr.id END) resolvedTickets
                FROM app_user u
                LEFT JOIN installation_job ij ON ij.tenant_id=u.tenant_id AND ij.assigned_engineer_id=u.id
                  AND ij.scheduled_at BETWEEN :from AND :to AND (:branchId IS NULL OR ij.branch_id=:branchId)
                LEFT JOIN service_request sr ON sr.tenant_id=u.tenant_id AND sr.assigned_engineer_id=u.id
                  AND sr.created_at BETWEEN :from AND :to AND (:branchId IS NULL OR sr.branch_id=:branchId)
                WHERE u.tenant_id=:tenantId AND u.enabled=TRUE AND (:employeeId IS NULL OR u.id=:employeeId)
                GROUP BY u.id,u.name ORDER BY completedInstallations DESC,u.name LIMIT 100
                """;
        String outstandingSql = """
                SELECT i.id,i.invoice_number invoiceNumber,i.customer_name customerName,i.grand_total grandTotal,
                       COALESCE(pay.received,0)-COALESCE(pay.refunded,0) paidAmount,
                       GREATEST(i.grand_total-(COALESCE(pay.received,0)-COALESCE(pay.refunded,0)),0) outstandingAmount
                FROM invoice i
                LEFT JOIN (
                  SELECT p.tenant_id,p.invoice_id,
                    SUM(CASE WHEN p.status IN ('CONFIRMED','SUCCEEDED','PAID','PARTIALLY_REFUNDED','REFUNDED') THEN p.amount ELSE 0 END) received,
                    SUM(CASE WHEN p.status IN ('CONFIRMED','SUCCEEDED','PAID','PARTIALLY_REFUNDED','REFUNDED') THEN COALESCE(r.refunded,0) ELSE 0 END) refunded
                  FROM payment p
                  LEFT JOIN (SELECT tenant_id,payment_id,SUM(amount) refunded FROM payment_refund WHERE status='RECORDED' GROUP BY tenant_id,payment_id) r
                    ON r.tenant_id=p.tenant_id AND r.payment_id=p.id
                  WHERE p.tenant_id=:tenantId GROUP BY p.tenant_id,p.invoice_id
                ) pay ON pay.tenant_id=i.tenant_id AND pay.invoice_id=i.id
                WHERE i.tenant_id=:tenantId AND i.created_at BETWEEN :from AND :to
                  AND (:branchId IS NULL OR i.branch_id=:branchId)
                  AND GREATEST(i.grand_total-(COALESCE(pay.received,0)-COALESCE(pay.refunded,0)),0)>0
                ORDER BY outstandingAmount DESC,i.created_at DESC LIMIT 100
                """;
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("installationsByStatus", jdbc.queryForList(installationsSql,p));
        result.put("serviceTicketsByStatus", jdbc.queryForList(servicesSql,p));
        result.put("lowStockProducts", jdbc.queryForList(stockSql,p));
        result.put("employeePerformance", jdbc.queryForList(employeeSql,p));
        result.put("outstandingInvoices", jdbc.queryForList(outstandingSql,p));
        result.put("from",p.getValue("from")); result.put("to",p.getValue("to"));
        result.put("branchId",branchId); result.put("employeeId",employeeId);
        result.put("scope","TENANT_SCOPED");
        return result;
    }

    private void mergeTrend(
            TreeMap<String, Map<String, Object>> series,
            String sql,
            String key,
            MapSqlParameterSource p,
            boolean decimal
    ) {
        for (Map<String, Object> row : jdbc.queryForList(sql, p)) {
            String period = String.valueOf(row.get("period"));
            Map<String, Object> item = series.computeIfAbsent(
                    period, k -> {
                        Map<String, Object> v = new LinkedHashMap<>();
                        v.put("period", k);
                        v.put("leads", 0L);
                        v.put("enquiries", 0L);
                        v.put("orders", 0L);
                        v.put("revenue", BigDecimal.ZERO);
                        return v;
                    }
            );
            item.put(key, row.get("metric_value"));
        }
    }
    
    public Map<String, Object> rankings(
            Principal actor,
            Instant from,
            Instant to,
            UUID branchId,
            UUID categoryId,
            UUID productId,
            String metric,
            int limit
    ) {
        if (limit < 1 || limit > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 50");
        String sort = switch (metric == null ? "orders" : metric.toLowerCase(Locale.ROOT)) {
            case "orders" -> "orders";
            case "revenue" -> "revenue";
            case "leads" -> "leads";
            default ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metric must be orders, revenue or leads");
        };
        Map<String, Object> data = breakdowns(actor, from, to, branchId, categoryId, productId);
        Comparator<Map<String, Object>> comparator = Comparator.comparing(m -> new java.math.BigDecimal(String.valueOf(m.getOrDefault(sort, 0))));
        List<Map<String, Object>> products = ( (List<Map<String, Object>>) data.get("products") ).stream().sorted(comparator.reversed()).limit(limit).toList();
        List<Map<String, Object>> categories = ( (List<Map<String, Object>>) data.get("categories") ).stream().sorted(comparator.reversed()).limit(limit).toList();
        return Map.of("metric", sort, "limit", limit, "topProducts", products, "topCategories", categories);
    }
}
