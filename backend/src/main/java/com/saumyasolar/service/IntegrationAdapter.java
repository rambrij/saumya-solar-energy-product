package com.saumyasolar.service;

import com.saumyasolar.domain.IntegrationConnection;
import com.saumyasolar.domain.IntegrationSyncJob;
public interface IntegrationAdapter {
    String providerCode();
    
    void synchronize(IntegrationConnection connection, IntegrationSyncJob job) throws Exception;
}
