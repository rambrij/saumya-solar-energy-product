package com.saumyasolar.api;

import com.saumyasolar.domain.SiteSurveyAttachment;
import com.saumyasolar.security.Principal;
import com.saumyasolar.service.SurveyAttachmentService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/surveys/{surveyId}/attachments")
public class SurveyAttachmentController {
    private final SurveyAttachmentService attachments;
    
    public SurveyAttachmentController(SurveyAttachmentService attachments) {
        this.attachments = attachments;
    }
    
    private Principal principal(Authentication a) {
        return (Principal) a.getPrincipal();
    }
    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SiteSurveyAttachment upload(
            @PathVariable UUID surveyId,
            @RequestPart("file") MultipartFile file,
            Authentication a
    ) {
        return attachments.upload(surveyId, file, principal(a));
    }
    
    @GetMapping
    public List<SiteSurveyAttachment> list(@PathVariable UUID surveyId, Authentication a) {
        return attachments.list(surveyId, principal(a));
    }
    
    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID surveyId,
            @PathVariable UUID attachmentId,
            Authentication a
    ) {
        var result = attachments.download(surveyId, attachmentId, principal(a));
        var item = result.attachment();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(item.getContentType())).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(item.getOriginalName()).build().toString()).contentLength(result.content().length).body(result.content());
    }
}
