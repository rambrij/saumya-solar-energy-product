package com.saumyasolar.api;

import com.saumyasolar.security.Principal;
import com.saumyasolar.service.CmsService;
import com.saumyasolar.service.PackageImageStorageService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CmsController {
    private final CmsService cms;
    private final PackageImageStorageService images;

    public CmsController(CmsService cms, PackageImageStorageService images) {
        this.cms = cms;
        this.images = images;
    }

    @PostMapping(value = "/cms/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadMedia(@RequestPart("file") MultipartFile file, Authentication authentication) {
        Principal actor = principal(authentication);
        cms.requireMediaUpload(actor);
        String storedUrl = images.store(file);
        java.net.URI storedUri = java.net.URI.create(storedUrl);
        String imageUrl = storedUri.isAbsolute() ? storedUri.getRawPath() : storedUrl;
        if (imageUrl == null || !imageUrl.matches("^/uploads/[A-Za-z0-9._-]+$"))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Uploaded media path is invalid");
        cms.recordMediaUpload(imageUrl, actor);
        return java.util.Map.of("imageUrl", imageUrl);
    }

    @GetMapping("/cms/content")
    public Page<?> list(@RequestParam(required = false) String type,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Authentication authentication) {
        return cms.list(principal(authentication), type, page, size);
    }

    @PostMapping("/cms/content")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(@RequestBody CmsService.ContentInput input, Authentication authentication) {
        var content = cms.create(input, principal(authentication));
        return cms.get(content.getId(), principal(authentication));
    }

    @GetMapping("/cms/content/{id}")
    public CmsService.ContentDetail get(@PathVariable UUID id, Authentication authentication) {
        return cms.get(id, principal(authentication));
    }

    @PatchMapping("/cms/content/{id}")
    public Object update(@PathVariable UUID id, @RequestBody CmsService.ContentInput input,
                         Authentication authentication) {
        cms.update(id, input, principal(authentication));
        return cms.get(id, principal(authentication));
    }

    @PostMapping("/cms/content/{id}/transition")
    public Object transition(@PathVariable UUID id, @RequestBody TransitionRequest request,
                             Authentication authentication) {
        cms.transition(id, request.status(), principal(authentication));
        return cms.get(id, principal(authentication));
    }

    @PostMapping("/cms/content/{id}/rollback")
    public Object rollback(@PathVariable UUID id, @RequestBody RollbackRequest request,
                           Authentication authentication) {
        cms.rollback(id, request.revisionNumber(), principal(authentication));
        return cms.get(id, principal(authentication));
    }

    @GetMapping("/public/{storeSlug}/content/{type}")
    public Page<CmsService.PublicContent> publicList(@PathVariable String storeSlug, @PathVariable String type,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return cms.publicList(storeSlug, type, page, size);
    }

    @GetMapping("/public/{storeSlug}/content/{type}/{slug}")
    public CmsService.PublicContent publicGet(@PathVariable String storeSlug, @PathVariable String type,
                                               @PathVariable String slug) {
        return cms.publicGet(storeSlug, type, slug);
    }

    private Principal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Principal actor))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return actor;
    }

    public record TransitionRequest(String status) { }
    public record RollbackRequest(int revisionNumber) { }
}
