package com.saumyasolar.service;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.saumyasolar.api.Phase2OperationsController;
import com.saumyasolar.domain.ExportJob;
import com.saumyasolar.repo.ExportJobRepository;
import com.saumyasolar.security.Principal;
import com.saumyasolar.security.TenantExecutionContext;
import jakarta.transaction.Transactional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class ExportService {
    private final ExportJobRepository jobs;
    private final ApplicationEventPublisher events;
    private final Phase2OperationsController reports;
    private final PrivateObjectStorageService storage;
    private final Path root;
    private final long retentionHours;
    @Value("\u0024{app.exports.processing-lease-seconds:1800}")
    private long processingLeaseSeconds = 1800;

    public ExportService(ExportJobRepository jobs, ApplicationEventPublisher events, Phase2OperationsController reports,
            PrivateObjectStorageService storage, @Value("\u0024{app.exports.location:./data/private-exports}") String location,
            @Value("\u0024{app.exports.retention-hours:24}") long retentionHours) {
        this.jobs=jobs; this.events=events; this.reports=reports; this.storage=storage;
        this.root=Path.of(location).toAbsolutePath().normalize(); this.retentionHours=Math.max(1,retentionHours);
    }

    @Transactional
    public ExportJob create(Principal p, String reportType, String format, LocalDate from, LocalDate to) {
        requireExportPermission(p);
        String type=reportType==null?"":reportType.trim().toUpperCase(Locale.ROOT);
        String output=format==null?"":format.trim().toUpperCase(Locale.ROOT);
        if (!"OPERATIONS".equals(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported report type");
        if (!Set.of("CSV","XLSX","PDF").contains(output)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Format must be CSV, XLSX or PDF");
        if (from!=null && to!=null && to.isBefore(from)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"to must not precede from");
        Instant now=Instant.now(); ExportJob job=new ExportJob();
        job.setId(UUID.randomUUID()); job.setTenantId(p.tenantId()); job.setRequestedBy(p.userId());
        job.setReportType(type); job.setFormat(output); job.setFromDate(from); job.setToDate(to);
        job.setStatus("PENDING"); job.setCreatedAt(now); job.setUpdatedAt(now); jobs.save(job);
        publishAfterCommit(job.getId(),job.getTenantId()); return job;
    }

    private void requireExportPermission(Principal p) {
        if (p==null || !"VENDOR".equals(p.accountType()) || p.tenantId()==null || p.userId()==null
                || p.permissions()==null || !p.permissions().contains("REPORT_EXPORT"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"REPORT_EXPORT permission is required");
    }

    private void publishAfterCommit(UUID id, UUID tenantId) {
        ExportRequestedEvent event=new ExportRequestedEvent(this,id,tenantId);
        if (TransactionSynchronizationManager.isSynchronizationActive() && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { events.publishEvent(event); }
            });
        } else events.publishEvent(event);
    }

    public void process(UUID id, UUID tenantId) {
        Instant now=Instant.now();
        if (jobs.claim(id,tenantId,now,now.minusSeconds(Math.max(60,processingLeaseSeconds)))!=1) return;
        ExportJob job=jobs.findByIdAndTenantId(id,tenantId).orElse(null);
        if (job==null || !"PROCESSING".equals(job.getStatus())) return;
        String key=null;
        try {
            Map<String,Object> report=reports.operationsReportForTenant(tenantId,job.getFromDate(),job.getToDate());
            FilePayload file=render(report,job);
            key=storage.putReplacing(root,"exports",tenantId,id+"."+file.extension(),file.bytes(),file.contentType());
            job.setStorageKey(key); job.setContentType(file.contentType()); job.setFileSize((long)file.bytes().length);
            job.setStatus("COMPLETED"); job.setCompletedAt(Instant.now()); job.setExpiresAt(Instant.now().plusSeconds(retentionHours*3600));
            job.setLastError(null);
        } catch (Exception ex) {
            if (key!=null) try { storage.delete(root,key); } catch (Exception ignored) { }
            job.setStatus("FAILED"); job.setLastError("Export generation failed; submit a new export request");
        }
        job.setUpdatedAt(Instant.now()); jobs.save(job);
    }

    private FilePayload render(Map<String,Object> report, ExportJob job) throws Exception {
        return switch(job.getFormat()) { case "CSV" -> csv(report); case "XLSX" -> xlsx(report); case "PDF" -> pdf(report,job); default -> throw new IllegalArgumentException("Unsupported format"); };
    }
    private FilePayload csv(Map<String,Object> report) {
        StringBuilder out=new StringBuilder("Metric,Value\\r\\n");
        report.forEach((k,v)->out.append(cell(k)).append(',').append(cell(v)).append("\\r\\n"));
        return new FilePayload(out.toString().getBytes(StandardCharsets.UTF_8),"text/csv; charset=UTF-8","csv");
    }
    private String cell(Object value) {
        String s=value==null?"":String.valueOf(value);
        if (!(value instanceof Number) && s.matches("^[\\s]*[=+\\-@].*")) s="'"+s;
        if (s.contains(",") || s.contains("\\\"") || s.contains("\\n") || s.contains("\\r")) return "\\\""+s.replace("\\\"","\\\"\\\"")+"\\\"";
        return s;
    }
    private FilePayload xlsx(Map<String,Object> report) throws Exception {
        try(ByteArrayOutputStream out=new ByteArrayOutputStream(); XSSFWorkbook wb=new XSSFWorkbook()) {
            var sheet=wb.createSheet("Operations"); var header=sheet.createRow(0);
            header.createCell(0).setCellValue("Metric"); header.createCell(1).setCellValue("Value"); int row=1;
            for(var entry:report.entrySet()){var cells=sheet.createRow(row++);cells.createCell(0).setCellValue(entry.getKey());cells.createCell(1).setCellValue(entry.getValue()==null?"":String.valueOf(entry.getValue()));}
            sheet.setColumnWidth(0,24*256);sheet.setColumnWidth(1,32*256);wb.write(out);
            return new FilePayload(out.toByteArray(),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","xlsx");
        }
    }
    private FilePayload pdf(Map<String,Object> report, ExportJob job) throws Exception {
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Document doc=new Document();PdfWriter.getInstance(doc,out);doc.open();doc.add(new Paragraph("Soumya Solar — Operations Report"));
            doc.add(new Paragraph("Period: "+(job.getFromDate()==null?"Beginning":job.getFromDate())+" to "+(job.getToDate()==null?"Today":job.getToDate())));
            PdfPTable table=new PdfPTable(2);table.setWidthPercentage(100);table.addCell("Metric");table.addCell("Value");
            for(var entry:report.entrySet()){table.addCell(entry.getKey());table.addCell(entry.getValue()==null?"":String.valueOf(entry.getValue()));}
            doc.add(table);doc.close();return new FilePayload(out.toByteArray(),"application/pdf","pdf");
        }
    }

    public ExportJob status(UUID id, Principal p) {
        requireExportPermission(p);
        return jobs.findByIdAndTenantIdAndRequestedBy(id,p.tenantId(),p.userId())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Export not found"));
    }
    public Download download(UUID id, Principal p) {
        ExportJob job=status(id,p);
        if(!"COMPLETED".equals(job.getStatus()) || job.getStorageKey()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Export is not ready for download");
        if(job.getExpiresAt()==null || !job.getExpiresAt().isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.GONE,"Export has expired");
        try{return new Download(job,storage.get(root,job.getStorageKey()));}
        catch(java.io.IOException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Export file is unavailable",ex);}
    }

    @Scheduled(fixedDelayString="\u0024{app.exports.recovery-interval-ms:60000}")
    public void recoverAndExpire() {
        Instant now=Instant.now(),stale=now.minusSeconds(Math.max(60,processingLeaseSeconds));
        for(UUID tenant:jobs.findDistinctTenantIdsForRecovery(stale))
            for(ExportJob job:jobs.findRecoverableByTenant(tenant,stale,org.springframework.data.domain.PageRequest.of(0,50))) publishAfterCommit(job.getId(),tenant);
        for (UUID tenantId : jobs.findDistinctTenantIdsForExpiry(now)) {
            try (TenantExecutionContext.Scope ignored = TenantExecutionContext.open(tenantId)) {
                for (ExportJob job : jobs.findTop100ByTenantIdAndStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        tenantId, "COMPLETED", now)) {
                    if (job.getStorageKey() != null) {
                        try { storage.delete(root, job.getStorageKey()); }
                        catch (Exception ignoredDeleteFailure) { /* Retain the expired DB state; orphan cleanup is retryable. */ }
                    }
                    job.setStorageKey(null);
                    job.setStatus("EXPIRED");
                    job.setUpdatedAt(now);
                    jobs.save(job);
                }
            }
        }
    }
    public record FilePayload(byte[] bytes,String contentType,String extension){}
    public record Download(ExportJob job,byte[] bytes){}
}
