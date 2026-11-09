package com.saumyasolar.service;

import com.saumyasolar.domain.IntegrationConnection;
import com.saumyasolar.domain.IntegrationSyncJob;

import java.util.Map;

/**
 * Provider-neutral accounting port. Implementations must be idempotent using the job key, must not trust tenant
 * identifiers from payloads, and must return only non-secret references.
 */
public interface AccountingAdapter extends IntegrationAdapter {
    AccountingResult synchronizeInvoice(
            IntegrationConnection connection, IntegrationSyncJob job,
            Map<String, Object> canonicalInvoice
    ) throws Exception;
    
    AccountingResult synchronizePayment(
            IntegrationConnection connection, IntegrationSyncJob job,
            Map<String, Object> canonicalPayment
    ) throws Exception;
    
    AccountingResult synchronizeRefund(
            IntegrationConnection connection, IntegrationSyncJob job,
            Map<String, Object> canonicalRefund
    ) throws Exception;
    
    record AccountingResult(String externalReference, String status, String message) {
    }
}
