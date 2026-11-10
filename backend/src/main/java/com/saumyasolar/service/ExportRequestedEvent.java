package com.saumyasolar.service;
import org.springframework.context.ApplicationEvent;
import java.util.UUID;

/** Event for a persisted tenant-scoped export job. */
public final class ExportRequestedEvent extends ApplicationEvent {
    private final UUID exportId;
    private final UUID tenantId;
    public ExportRequestedEvent(Object source, UUID exportId, UUID tenantId) {
        super(source);
        if (exportId == null || tenantId == null) throw new IllegalArgumentException("exportId and tenantId are required");
        this.exportId = exportId; this.tenantId = tenantId;
    }
    public UUID exportId() { return exportId; }
    public UUID tenantId() { return tenantId; }
}
