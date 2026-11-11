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
        if (!Set.of("OPERATIONS","SALES","INSTALLATIONS","SERVICE","INVENTORY","FINANCE").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported report type");
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
            Map<String,Object> fullReport=reports.operationsReportForTenant(tenantId,job.getFromDate(),job.getToDate());
            Map<String,Object> report=selectReport(job.getReportType(),fullReport);
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
    private Map<String,Object> selectReport(String type, Map<String,Object> full) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("from",full.get("from")); out.put("to",full.get("to"));
        switch(type) {
            case "SALES" -> { copy(full,out,"orders","orderValue","leads","salesOrders"); }
            case "INSTALLATIONS" -> { copy(full,out,"installations","installationsCompleted","installationDetails"); }
            case "SERVICE" -> { copy(full,out,"serviceTickets","serviceOpen","serviceTicketDetails"); }
            case "INVENTORY" -> { copy(full,out,"inventoryTransactions","inventoryMovementDetails"); }
            case "FINANCE" -> { copy(full,out,"invoices","invoicedAmount","paymentsReceived","invoiceDetails","paymentDetails"); }
            default -> out.putAll(full);
        }
        out.put("reportType",type);
        return out;
    }
    private void copy(Map<String,Object> source,Map<String,Object> target,String... keys) {
        for(String key:keys) if(source.containsKey(key)) target.put(key,source.get(key));
    }

    private FilePayload csv(Map<String,Object> report) {
        StringBuilder out=new StringBuilder("Metric,Value\r\n");
        out.append("reportType,").append(cell(report.getOrDefault("reportType","OPERATIONS"))).append("\r\n");
        for(var entry:report.entrySet()) {
            if(entry.getValue() instanceof List<?> rows && !rows.isEmpty() && rows.get(0) instanceof Map<?,?> first) {
                out.append("\r\n").append(cell(entry.getKey())).append("\r\n");
                List<?> keys=new ArrayList<>(first.keySet());
                out.append(keys.stream().map(this::cell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
                for(Object item:rows) if(item instanceof Map<?,?> row)
                    out.append(keys.stream().map(key->cell(row.get(key))).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
            } else if(!(entry.getValue() instanceof List<?>)) {
                out.append(cell(entry.getKey())).append(',').append(cell(entry.getValue())).append("\r\n");
            }
        }
        return new FilePayload(out.toString().getBytes(StandardCharsets.UTF_8),"text/csv; charset=UTF-8","csv");
    }
    private String cell(Object value) {
        String s=value==null?"":String.valueOf(value);
        if (!(value instanceof Number) && s.matches("^[\\s]*[=+\\-@].*")) s="'"+s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r"))
            return "\""+s.replace("\"","\"\"")+"\"";
        return s;
    }
    private FilePayload xlsx(Map<String,Object> report) throws Exception {
        try(ByteArrayOutputStream out=new ByteArrayOutputStream(); XSSFWorkbook wb=new XSSFWorkbook()) {
            var summary=wb.createSheet("Summary");var header=summary.createRow(0);
            header.createCell(0).setCellValue("Metric");header.createCell(1).setCellValue("Value");int row=1;
            for(var entry:report.entrySet()) {
                if(entry.getValue() instanceof List<?> rows && !rows.isEmpty() && rows.get(0) instanceof Map<?,?> first) {
                    String sheetName=entry.getKey().replaceAll("[\\[\\]\\*?/\\\\:]","_");
                    var sheet=wb.createSheet(sheetName.substring(0,Math.min(31,sheetName.length())));
                    List<?> keys=new ArrayList<>(first.keySet());var h=sheet.createRow(0);
                    for(int i=0;i<keys.size();i++)h.createCell(i).setCellValue(String.valueOf(keys.get(i)));
                    int line=1;for(Object item:rows)if(item instanceof Map<?,?> values){var data=sheet.createRow(line++);for(int i=0;i<keys.size();i++){Object value=values.get(keys.get(i));data.createCell(i).setCellValue(value==null?"":String.valueOf(value));}}
                    for(int i=0;i<keys.size();i++)sheet.autoSizeColumn(i);
                } else if(!(entry.getValue() instanceof List<?>)) {
                    var data=summary.createRow(row++);data.createCell(0).setCellValue(entry.getKey());data.createCell(1).setCellValue(entry.getValue()==null?"":String.valueOf(entry.getValue()));
                }
            }
            // Keep the generic Summary sheet for existing consumers and expose the
            // Operations report under its stable, report-specific worksheet name.
            if ("OPERATIONS".equalsIgnoreCase(String.valueOf(report.get("reportType")))) {
                var operations=wb.createSheet("Operations");
                var operationsHeader=operations.createRow(0);
                operationsHeader.createCell(0).setCellValue("Metric");
                operationsHeader.createCell(1).setCellValue("Value");
                int operationsRow=1;
                for (var entry : report.entrySet()) {
                    if (!(entry.getValue() instanceof List<?>)) {
                        var data=operations.createRow(operationsRow++);
                        data.createCell(0).setCellValue(entry.getKey());
                        data.createCell(1).setCellValue(entry.getValue()==null?"":String.valueOf(entry.getValue()));
                    }
                }
                operations.autoSizeColumn(0);operations.autoSizeColumn(1);
            }
            summary.autoSizeColumn(0);summary.autoSizeColumn(1);wb.write(out);
            return new FilePayload(out.toByteArray(),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","xlsx");
        }
    }
    private FilePayload pdf(Map<String,Object> report, ExportJob job) throws Exception {
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Document doc=new Document();PdfWriter.getInstance(doc,out);doc.open();
            doc.add(new Paragraph("Saumya Solar — "+job.getReportType()+" Report"));
            doc.add(new Paragraph("Period: "+(job.getFromDate()==null?"Beginning":job.getFromDate())+" to "+(job.getToDate()==null?"Today":job.getToDate())));
            PdfPTable summary=new PdfPTable(2);summary.setWidthPercentage(100);summary.addCell("Metric");summary.addCell("Value");
            for(var entry:report.entrySet()) if(!(entry.getValue() instanceof List<?>)){summary.addCell(entry.getKey());summary.addCell(entry.getValue()==null?"":String.valueOf(entry.getValue()));}
            doc.add(summary);
            for(var entry:report.entrySet()) if(entry.getValue() instanceof List<?> rows && !rows.isEmpty() && rows.get(0) instanceof Map<?,?> first) {
                doc.add(new Paragraph(entry.getKey()));
                List<?> keys=new ArrayList<>(first.keySet());PdfPTable table=new PdfPTable(Math.max(1,keys.size()));table.setWidthPercentage(100);
                for(Object key:keys)table.addCell(String.valueOf(key));
                for(Object item:rows)if(item instanceof Map<?,?> values)for(Object key:keys)table.addCell(values.get(key)==null?"":String.valueOf(values.get(key)));
                doc.add(table);
            }
            doc.close();return new FilePayload(out.toByteArray(),"application/pdf","pdf");
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
        for (UUID tenant : jobs.findDistinctTenantIdsForRecovery(stale)) {
            try (TenantExecutionContext.Scope ignored = TenantExecutionContext.open(tenant)) {
                for (ExportJob job : jobs.findRecoverableByTenant(
                        tenant, stale, org.springframework.data.domain.PageRequest.of(0, 50))) {
                    publishAfterCommit(job.getId(), tenant);
                }
            }
        }
        for (UUID tenantId : jobs.findDistinctTenantIdsForExpiry(now)) {
            try (TenantExecutionContext.Scope ignored = TenantExecutionContext.open(tenantId)) {
                for (ExportJob job : jobs.findTop100ByTenantIdAndStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        tenantId, "COMPLETED", now)) {
                    if (job.getStorageKey() != null) {
                        try { storage.delete(root, job.getStorageKey()); }
                        catch (Exception deleteFailure) {
                            // Keep the completed row and storage key so the next scheduler pass retries deletion.
                            continue;
                        }
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
