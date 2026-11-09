package com.saumyasolar.api;

import com.saumyasolar.domain.WarrantyClaimAttachment;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.WarrantyClaimAttachmentService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/warranty-claims/{claimId}/attachments")
public class WarrantyClaimAttachmentController {
    private final WarrantyClaimAttachmentService attachments;
    
    public WarrantyClaimAttachmentController(WarrantyClaimAttachmentService attachments) {
        this.attachments = attachments;
    }
    
    private Principal principal(Authentication a) {
        return (Principal) a.getPrincipal();
    }
    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WarrantyClaimAttachment upload(
            @PathVariable UUID claimId,
            @RequestPart("file") MultipartFile file,
            Authentication a
    ) {
        return attachments.upload(claimId, file, principal(a));
    }
    
    @GetMapping
    public List<WarrantyClaimAttachment> list(@PathVariable UUID claimId, Authentication a) {
        return attachments.list(claimId, principal(a));
    }
    
    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID claimId,
            @PathVariable UUID attachmentId,
            Authentication a
    ) {
        var result = attachments.download(claimId, attachmentId, principal(a));
        var item = result.attachment();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(item.getContentType())).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(item.getOriginalName()).build().toString()).contentLength(result.content().length).body(result.content());
    }
}
