package com.saumyasolar.api;

import com.saumyasolar.domain.ExportJob;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.ExportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {
    private final ExportService exports;
    public ExportController(ExportService exports){this.exports=exports;}
    public record CreateExportRequest(@NotBlank String reportType,@NotBlank String format,LocalDate from,LocalDate to){}
    public record ExportJobResponse(UUID id,String reportType,String format,String status,LocalDate from,LocalDate to,
            Long fileSize,java.time.Instant createdAt,java.time.Instant expiresAt,String error,String downloadUrl){}
    private Principal principal(Authentication a){return (Principal)a.getPrincipal();}
    private ExportJobResponse view(ExportJob j){
        String url="COMPLETED".equals(j.getStatus())?"/api/v1/exports/"+j.getId()+"/download":null;
        String error = "FAILED".equals(j.getStatus()) ? "Export failed. Please retry or contact support." : null;
        return new ExportJobResponse(j.getId(),j.getReportType(),j.getFormat(),j.getStatus(),j.getFromDate(),j.getToDate(),j.getFileSize(),j.getCreatedAt(),j.getExpiresAt(),error,url);
    }
    @PostMapping @Valid
    public ResponseEntity<ExportJobResponse> create(@Valid @RequestBody CreateExportRequest x,Authentication a){
        ExportJob job=exports.create(principal(a),x.reportType(),x.format(),x.from(),x.to());
        return ResponseEntity.accepted().location(java.net.URI.create("/api/v1/exports/"+job.getId())).body(view(job));
    }
    @GetMapping("/{exportId}") public ExportJobResponse status(@PathVariable UUID exportId,Authentication a){return view(exports.status(exportId,principal(a)));}
    @GetMapping("/{exportId}/download") public ResponseEntity<byte[]> download(@PathVariable UUID exportId,Authentication a){
        ExportService.Download d=exports.download(exportId,principal(a));ExportJob j=d.job();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(j.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("operations-report-"+j.getId()+"."+j.getFormat().toLowerCase(java.util.Locale.ROOT)).build().toString())
                .contentLength(d.bytes().length).body(d.bytes());
    }
}
