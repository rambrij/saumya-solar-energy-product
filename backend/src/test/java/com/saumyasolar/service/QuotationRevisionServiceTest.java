package com.saumyasolar.service;

import com.saumyasolar.domain.Quotation;
import com.saumyasolar.repo.QuotationItemRepository;
import com.saumyasolar.repo.QuotationRepository;
import com.saumyasolar.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuotationRevisionServiceTest {
    @Test
    void assignedSalesScopeIsCheckedBeforeSupersedingQuotation() {
        UUID tenantId = UUID.randomUUID();
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = new Quotation();
        quotation.setId(quotationId);
        quotation.setTenantId(tenantId);
        quotation.setStatus("SENT");

        QuotationRepository quotes = mock(QuotationRepository.class);
        QuotationItemRepository items = mock(QuotationItemRepository.class);
        AppService appService = mock(AppService.class);
        Principal principal = new Principal(UUID.randomUUID(), tenantId, "sales@example.test",
                "VENDOR", Set.of("SALES_EXECUTIVE"), Set.of("QUOTATION_UPDATE", "LEAD_VIEW"));
        when(quotes.findByIdAndTenantId(quotationId, tenantId)).thenReturn(Optional.of(quotation));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"))
                .when(appService).assertSalesQuotationScope(quotation, principal);

        QuotationRevisionService service = new QuotationRevisionService(quotes, items, appService);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.revise(quotationId, principal));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        assertEquals("SENT", quotation.getStatus());
        verify(quotes, never()).save(any(Quotation.class));
        verifyNoInteractions(items);
    }
}
