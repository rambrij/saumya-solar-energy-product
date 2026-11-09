package com.saumyasolar.service;

import com.saumyasolar.api.Models.QuoteView;
import com.saumyasolar.domain.Quotation;
import com.saumyasolar.domain.QuotationItem;
import com.saumyasolar.repo.QuotationItemRepository;
import com.saumyasolar.repo.QuotationRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
@Service
public class QuotationRevisionService {
    private final QuotationRepository quotes;
    private final QuotationItemRepository items;
    
    public QuotationRevisionService(QuotationRepository q, QuotationItemRepository i) {
        quotes = q;
        items = i;
    }
    
    @Transactional
    public QuoteView revise(UUID id, Principal p) {
        if (!"VENDOR".equals(p.accountType()) || p.tenantId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (p.permissions() == null || !p.permissions().contains("QUOTATION_UPDATE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: QUOTATION_UPDATE");
        var old = quotes.findByIdAndTenantId(id, p.tenantId()).orElseThrow();
        if (!Set.of("SENT", "REJECTED", "APPROVAL_REJECTED").contains(old.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only sent/rejected quotations can be revised");
        old.setStatus("SUPERSEDED");
        quotes.save(old);
        var q = new Quotation();
        q.setId(UUID.randomUUID());
        q.setTenantId(p.tenantId());
        q.setQuoteGroupId(old.getQuoteGroupId());
        q.setQuotationNumber(old.getQuotationNumber());
        q.setRevision(old.getRevision() + 1);
        q.setCustomerId(old.getCustomerId());
        q.setLeadId(old.getLeadId());
        q.setSurveyId(old.getSurveyId());
        q.setStatus("DRAFT");
        q.setSubtotal(old.getSubtotal());
        q.setDiscountPercent(old.getDiscountPercent());
        q.setDiscountAmount(old.getDiscountAmount());
        q.setTaxAmount(old.getTaxAmount());
        q.setGrandTotal(old.getGrandTotal());
        q.setValidUntil(old.getValidUntil());
        q.setApprovalRequired(old.isApprovalRequired());
        q.setCreatedBy(p.userId());
        q.setCreatedAt(Instant.now());
        quotes.save(q);
        var copied = new ArrayList<QuotationItem>();
        for (var x : items.findByTenantIdOrderByIdDesc(p.tenantId()).stream().filter(i -> i.getQuotationId().equals(old.getId())).toList()) {
            var n = new QuotationItem();
            n.setId(UUID.randomUUID());
            n.setTenantId(p.tenantId());
            n.setQuotationId(q.getId());
            n.setProductId(x.getProductId());
            n.setPackageId(x.getPackageId());
            n.setDescription(x.getDescription());
            n.setQuantity(x.getQuantity());
            n.setUnitPrice(x.getUnitPrice());
            n.setTaxPercent(x.getTaxPercent());
            n.setLineTotal(x.getLineTotal());
            n.setDiscountAmount(x.getDiscountAmount());
            n.setTaxableAmount(x.getTaxableAmount());
            n.setTaxAmount(x.getTaxAmount());
            copied.add(items.save(n));
        }
        return new QuoteView(q, copied);
    }
}
