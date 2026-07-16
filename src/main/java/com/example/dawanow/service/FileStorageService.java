package com.example.dawanow.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

import com.example.dawanow.exception.FileTooLargeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    @Value("${app.upload.dir:uploads/licenses}")
    private String uploadDir;

    @Value("${app.upload.max-size-mb:15}")
    private long maxSizeMb;

    private static final String EXPECTED_CONTENT_TYPE = "application/pdf";
    private static final byte[] PDF_MAGIC_BYTES = {0x25, 0x50, 0x44, 0x46}; // "%PDF"

    public String storeLicenseFile(MultipartFile file) {
        validate(file);

        try {
            Path dirPath = Paths.get(uploadDir);
            Files.createDirectories(dirPath);

            String filename = UUID.randomUUID() + ".pdf";
            Path target = dirPath.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store license file", e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("License file is required");
        }

        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new FileTooLargeException("License file must not exceed " + maxSizeMb + "MB");
        }

        if (!EXPECTED_CONTENT_TYPE.equals(file.getContentType())) {
            throw new IllegalArgumentException("License file must be a PDF");
        }

        if (!hasPdfMagicBytes(file)) {
            throw new IllegalArgumentException("License file is not a valid PDF");
        }
    }

    private boolean hasPdfMagicBytes(MultipartFile file) {
        try (var in = file.getInputStream()) {
            byte[] header = new byte[4];
            int read = in.read(header);
            return read == 4 && java.util.Arrays.equals(header, PDF_MAGIC_BYTES);
        } catch (IOException e) {
            return false;
        }
    }
}