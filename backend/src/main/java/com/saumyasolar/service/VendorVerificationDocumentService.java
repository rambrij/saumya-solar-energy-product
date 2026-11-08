package com.saumyasolar.service;

import com.saumyasolar.api.VendorProfileModels.DocumentReviewInput;
import com.saumyasolar.api.VendorProfileModels.VerificationDocumentView;
import com.saumyasolar.domain.VendorVerificationDocument;
import com.saumyasolar.repo.VendorVerificationDocumentRepository;
import com.saumyasolar.security.Principal;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Service
public class VendorVerificationDocumentService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("GST_CERTIFICATE", "PAN", "BUSINESS_REGISTRATION", "ADDRESS_PROOF", "OTHER");
    private final Path root;
    private final VendorVerificationDocumentRepository documents;

    public VendorVerificationDocumentService(
            @Value("${app.vendor.kyc-location:./data/private-vendor-verification}") String location,
            VendorVerificationDocumentRepository documents) {
        this.root = Path.of(location).toAbsolutePath().normalize();
        this.documents = documents;
    }

    private UUID vendorTenant(Principal actor) {
        if (!"VENDOR".equals(actor.accountType()) || actor.tenantId() == null
                || actor.roles() == null || !actor.roles().contains("VENDOR_ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vendor administrator access is required");
        return actor.tenantId();
    }

    private void platformAdmin(Principal actor) {
        if (!"PLATFORM".equals(actor.accountType()) || actor.roles() == null
                || !actor.roles().contains("PLATFORM_SUPER_ADMIN") || actor.permissions() == null
                || !actor.permissions().contains("PLATFORM_MANAGE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform Super Admin permission is required");
    }

    @Transactional
    public VerificationDocumentView upload(String type, MultipartFile file, Principal actor) {
        UUID tenantId = vendorTenant(actor);
        String documentType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(documentType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supported vendor verification document type");
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a document file");
        if (file.getSize() > MAX_SIZE)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Verification documents must be 10 MB or smaller");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!Set.of("application/pdf", "image/jpeg", "image/png").contains(contentType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, JPEG and PNG verification documents are supported");
        try {
            byte[] bytes = file.getBytes();
            if (!valid(bytes, contentType))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File signature does not match its content type");
            String ext = switch (contentType) {
                case "application/pdf" -> ".pdf";
                case "image/jpeg" -> ".jpg";
                default -> ".png";
            };
            String key = UUID.randomUUID() + ext;
            Files.createDirectories(root);
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document path");
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            try {
                VendorVerificationDocument doc = new VendorVerificationDocument();
                doc.setId(UUID.randomUUID());
                doc.setTenantId(tenantId);
                doc.setDocumentType(documentType);
                doc.setOriginalName(safeName(file.getOriginalFilename()));
                doc.setStorageKey(key);
                doc.setContentType(contentType);
                doc.setSizeBytes(bytes.length);
                doc.setUploadedBy(actor.userId());
                doc.setReviewStatus("PENDING");
                doc.setCreatedAt(Instant.now());
                return view(documents.save(doc));
            } catch (RuntimeException ex) {
                Files.deleteIfExists(target);
                throw ex;
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store verification document", ex);
        }
    }

    public List<VerificationDocumentView> vendorList(Principal actor) {
        return documents.findByTenantIdOrderByCreatedAtDesc(vendorTenant(actor)).stream().map(this::view).toList();
    }

    public List<VerificationDocumentView> platformList(UUID tenantId, Principal actor) {
        platformAdmin(actor);
        return documents.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::view).toList();
    }

    public Download download(UUID id, Principal actor) {
        VendorVerificationDocument doc = documents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification document not found"));
        if ("PLATFORM".equals(actor.accountType())) platformAdmin(actor);
        else if (!Objects.equals(vendorTenant(actor), doc.getTenantId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification document not found");
        try {
            Path file = root.resolve(doc.getStorageKey()).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification document file not found");
            return new Download(view(doc), Files.readAllBytes(file));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read verification document", ex);
        }
    }

    @Transactional
    public VerificationDocumentView review(UUID id, DocumentReviewInput input, Principal actor) {
        platformAdmin(actor);
        VendorVerificationDocument doc = documents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification document not found"));
        String decision = input.decision().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVED", "REJECTED").contains(decision))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        if ("REJECTED".equals(decision) && (input.notes() == null || input.notes().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A review reason is required for rejected documents");
        doc.setReviewStatus(decision);
        doc.setReviewNotes(input.notes() == null ? null : input.notes().trim());
        doc.setReviewedBy(actor.userId());
        doc.setReviewedAt(Instant.now());
        return view(documents.save(doc));
    }

    private VerificationDocumentView view(VendorVerificationDocument doc) {
        return new VerificationDocumentView(doc.getId(), doc.getTenantId(), doc.getDocumentType(), doc.getOriginalName(),
                doc.getContentType(), doc.getSizeBytes(), doc.getReviewStatus(), doc.getReviewNotes(),
                doc.getCreatedAt(), doc.getReviewedAt());
    }

    private boolean valid(byte[] b, String type) {
        if ("application/pdf".equals(type)) return b.length >= 5 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-';
        if ("image/jpeg".equals(type)) return b.length >= 3 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255;
        return b.length >= 8 && (b[0]&255)==137 && b[1]=='P' && b[2]=='N' && b[3]=='G';
    }

    private String safeName(String value) {
        if (value == null || value.isBlank()) return "vendor-verification-document";
        String normalized = value.replace("\\", "/");
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    public record Download(VerificationDocumentView metadata, byte[] content) {}
}
