package com.saumyasolar.api;

import com.saumyasolar.domain.ServiceAttachment;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.ServiceAttachmentService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests/{ticketId}/attachments")
public class ServiceAttachmentController {
    private final ServiceAttachmentService attachments;
    
    public ServiceAttachmentController(ServiceAttachmentService attachments) {
        this.attachments = attachments;
    }
    
    private Principal principal(Authentication a) {
        return (Principal) a.getPrincipal();
    }
    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ServiceAttachment upload(
            @PathVariable UUID ticketId,
            @RequestPart("file") @NotNull MultipartFile file,
            Authentication a
    ) {
        return attachments.upload(ticketId, file, principal(a));
    }
    
    @GetMapping
    List<ServiceAttachment> list(@PathVariable UUID ticketId, Authentication a) {
        return attachments.list(ticketId, principal(a));
    }
    
    @GetMapping("/{attachmentId}")
    ResponseEntity<byte[]> download(@PathVariable UUID ticketId, @PathVariable UUID attachmentId, Authentication a) {
        var result = attachments.download(ticketId, attachmentId, principal(a));
        var item = result.attachment();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(item.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(item.getOriginalName()).build().toString())
                .contentLength(result.content().length).body(result.content());
    }
}
