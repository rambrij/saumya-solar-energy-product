package com.saumyasolar.repo;
import com.saumyasolar.domain.InvoiceItem;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem,UUID>{List<InvoiceItem> findByTenantIdAndInvoiceId(UUID tenantId,UUID invoiceId);}
