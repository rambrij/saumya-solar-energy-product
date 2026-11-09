package com.saumyasolar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PackageImageStorageService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
    private final Path root;
    private final String publicBaseUrl;
    
    public PackageImageStorageService(
            @Value("${app.storage.location:./data/uploads}") String location,
            @Value("${app.storage.public-base-url:}") String publicBaseUrl
    ) {
        this.root = Path.of(location).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }
    
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a package image.");
        if (file.getSize() > MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Package images must be 5 MB or smaller.");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG and WebP images are supported.");
        try {
            byte[] bytes = file.getBytes();
            if (!validImage(bytes, type))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid " + type + " image.");
            String extension = switch (type) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                default -> ".webp";
            };
            String filename = UUID.randomUUID() + extension;
            Files.createDirectories(root);
            Path destination = root.resolve(filename).normalize();
            if (!destination.startsWith(root))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image path.");
            Files.write(destination, bytes);
            return publicBaseUrl + "/uploads/" + filename;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store package image.", ex);
        }
    }
    
    private boolean validImage(byte[] bytes, String contentType) throws IOException {
        if (bytes.length < 12) return false;
        boolean signature = switch (contentType) {
            case "image/jpeg" -> ( bytes[0] & 0xff ) == 0xff && ( bytes[1] & 0xff ) == 0xd8;
            case "image/png" -> bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
            case "image/webp" -> bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
        if (!signature) return false;
        if (!"image/webp".equals(contentType)) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            return image != null && image.getWidth() > 0 && image.getHeight() > 0
                    && image.getWidth() <= 10000 && image.getHeight() <= 10000;
        }
        return true;
    }
}
