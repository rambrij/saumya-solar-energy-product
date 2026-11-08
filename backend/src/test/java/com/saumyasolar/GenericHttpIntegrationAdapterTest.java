package com.saumyasolar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumyasolar.domain.IntegrationConnection;
import com.saumyasolar.domain.IntegrationSyncJob;
import com.saumyasolar.service.GenericHttpIntegrationAdapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenericHttpIntegrationAdapterTest {
    private final GenericHttpIntegrationAdapter adapter = new GenericHttpIntegrationAdapter(new ObjectMapper());

    @Test
    void rejectsPlainHttpEndpoints() {
        IntegrationConnection connection = connection("{\"endpoint\":\"http://example.com/sync\"}");
        assertThrows(IllegalArgumentException.class, () -> adapter.synchronize(connection, job()));
    }

    @Test
    void rejectsLoopbackAndPrivateHostnames() {
        IntegrationConnection connection = connection("{\"endpoint\":\"https://localhost/sync\"}");
        assertThrows(IllegalArgumentException.class, () -> adapter.synchronize(connection, job()));
    }

    @Test
    void rejectsNonStandardPorts() {
        IntegrationConnection connection = connection("{\"endpoint\":\"https://api.example.com:8443/sync\"}");
        assertThrows(IllegalArgumentException.class, () -> adapter.synchronize(connection, job()));
    }

    private IntegrationConnection connection(String config) {
        IntegrationConnection value = new IntegrationConnection();
        value.setConfigJson(config);
        return value;
    }

    private IntegrationSyncJob job() {
        IntegrationSyncJob value = new IntegrationSyncJob();
        value.setPayloadJson("{}");
        value.setIdempotencyKey("unit-test-1");
        return value;
    }
}
