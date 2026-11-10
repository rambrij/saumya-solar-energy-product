package com.saumyasolar.service;

import com.saumyasolar.security.TenantExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ExportEventListener implements ApplicationListener<ExportRequestedEvent> {
    private static final Logger log = LoggerFactory.getLogger(ExportEventListener.class);
    private final ExportService exports;
    public ExportEventListener(ExportService exports) { this.exports = exports; }
    @Override @Async("applicationEventExecutor")
    public void onApplicationEvent(ExportRequestedEvent event) {
        try (TenantExecutionContext.Scope ignored = TenantExecutionContext.open(event.tenantId())) {
            exports.process(event.exportId(), event.tenantId());
        } catch (RuntimeException ex) {
            log.error("Export processing failed for tenant {} and export {}", event.tenantId(), event.exportId(), ex);
        }
    }
}
