package com.saumyasolar.repo;
import com.saumyasolar.domain.CreditNote;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;import java.util.UUID;
public interface CreditNoteRepository extends JpaRepository<CreditNote,UUID>{List<CreditNote> findByTenantIdAndInvoiceIdOrderByCreatedAtDesc(UUID tenantId,UUID invoiceId);List<CreditNote> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);}
