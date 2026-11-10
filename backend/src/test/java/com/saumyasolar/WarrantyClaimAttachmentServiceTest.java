package com.saumyasolar;

import com.saumyasolar.domain.CustomerProfile;
import com.saumyasolar.domain.WarrantyClaim;
import com.saumyasolar.repo.CustomerProfileRepository;
import com.saumyasolar.repo.WarrantyClaimAttachmentRepository;
import com.saumyasolar.repo.WarrantyClaimRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.PrivateObjectStorageService;
import com.saumyasolar.service.WarrantyClaimAttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarrantyClaimAttachmentServiceTest {
    @Mock WarrantyClaimAttachmentRepository attachments;
    @Mock WarrantyClaimRepository claims;
    @Mock CustomerProfileRepository customers;
    @Mock PrivateObjectStorageService storage;

    @TempDir Path tempDir;
    WarrantyClaimAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new WarrantyClaimAttachmentService(
                tempDir.toString(), attachments, claims, customers, storage
        );
    }

    @Test
    void tenantCannotDownloadAttachmentByGuessingForeignClaimId() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID claimB = UUID.randomUUID();
        UUID attachmentB = UUID.randomUUID();
        when(claims.findByIdAndTenantId(claimB, tenantA)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.download(claimB, attachmentB, vendor(tenantA)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(attachments, storage);
    }

    @Test
    void tenantCannotDownloadAnotherClaimsAttachmentUsingKnownAttachmentId() {
        UUID tenant = UUID.randomUUID();
        UUID claim = UUID.randomUUID();
        UUID attachment = UUID.randomUUID();
        when(claims.findByIdAndTenantId(claim, tenant)).thenReturn(Optional.of(claim(claim, tenant, UUID.randomUUID())));
        when(attachments.findByIdAndTenantIdAndClaimId(attachment, tenant, claim)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.download(claim, attachment, vendor(tenant)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(storage);
    }

    @Test
    void customerCannotDownloadAnotherCustomersClaimEvidence() {
        UUID tenant = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID claimB = UUID.randomUUID();
        UUID attachmentB = UUID.randomUUID();

        CustomerProfile profile = new CustomerProfile();
        profile.setId(customerA);
        profile.setTenantId(tenant);
        profile.setUserId(userA);
        profile.setActive(true);
        when(claims.findByIdAndTenantId(claimB, tenant))
                .thenReturn(Optional.of(claim(claimB, tenant, customerB)));
        when(customers.findByTenantIdAndUserId(tenant, userA)).thenReturn(Optional.of(profile));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.download(claimB, attachmentB, customer(tenant, userA)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(attachments, storage);
    }

    @Test
    void ownerCanDownloadOnlyTheAttachmentBelongingToTheirClaim() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String storageKey = tenant + "/warranty-claims/evidence.pdf";
        var claim = claim(claimId, tenant, customerId);
        var attachment = new com.saumyasolar.domain.WarrantyClaimAttachment();
        attachment.setId(attachmentId);
        attachment.setTenantId(tenant);
        attachment.setClaimId(claimId);
        attachment.setStorageKey(storageKey);
        attachment.setOriginalName("evidence.pdf");
        attachment.setContentType("application/pdf");
        byte[] expected = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(claims.findByIdAndTenantId(claimId, tenant)).thenReturn(Optional.of(claim));
        when(attachments.findByIdAndTenantIdAndClaimId(attachmentId, tenant, claimId))
                .thenReturn(Optional.of(attachment));
        when(storage.get(tempDir.toAbsolutePath().normalize(), storageKey)).thenReturn(expected);

        var downloaded = service.download(claimId, attachmentId, vendor(tenant));

        assertSame(attachment, downloaded.attachment());
        assertArrayEquals(expected, downloaded.content());
        verify(attachments).findByIdAndTenantIdAndClaimId(attachmentId, tenant, claimId);
    }

    @Test
    void customerCannotUploadEvidenceToAnotherCustomersClaim() {
        UUID tenant = UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID claimB = UUID.randomUUID();

        CustomerProfile profile = new CustomerProfile();
        profile.setId(customerA);
        profile.setTenantId(tenant);
        profile.setUserId(userA);
        profile.setActive(true);
        when(claims.findByIdAndTenantId(claimB, tenant))
                .thenReturn(Optional.of(claim(claimB, tenant, customerB)));
        when(customers.findByTenantIdAndUserId(tenant, userA)).thenReturn(Optional.of(profile));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.upload(claimB, null, customer(tenant, userA)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(attachments, storage);
    }

    private WarrantyClaim claim(UUID id, UUID tenant, UUID customer) {
        WarrantyClaim claim = new WarrantyClaim();
        claim.setId(id);
        claim.setTenantId(tenant);
        claim.setCustomerId(customer);
        return claim;
    }

    private Principal vendor(UUID tenant) {
        return new Principal(UUID.randomUUID(), tenant, "vendor@example.test", "VENDOR",
                Set.of("VENDOR_ADMIN"), Set.of("SERVICE_UPDATE"), 0);
    }

    private Principal customer(UUID tenant, UUID user) {
        return new Principal(user, tenant, "customer@example.test", "CUSTOMER",
                Set.of("CUSTOMER"), Set.of(), 0);
    }
}
