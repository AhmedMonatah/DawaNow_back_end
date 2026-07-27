package com.example.dawanow.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MedicineImageValidator {

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    public ValidatedImage read(MultipartFile image, String imageLabel) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException(imageLabel + " image is required");
        }
        if (!SUPPORTED_CONTENT_TYPES.contains(image.getContentType())) {
            throw new IllegalArgumentException(imageLabel + " image must be JPEG or PNG");
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + imageLabel.toLowerCase() + " image", exception);
        }

        validateSignature(bytes, image.getContentType(), imageLabel);
        return new ValidatedImage(bytes, image.getContentType());
    }

    private void validateSignature(byte[] bytes, String contentType, String imageLabel) {
        boolean validJpeg = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
        boolean validPng = bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
        if (("image/jpeg".equals(contentType) && !validJpeg)
                || ("image/png".equals(contentType) && !validPng)) {
            throw new IllegalArgumentException(imageLabel + " image content is invalid");
        }
    }

    public record ValidatedImage(byte[] bytes, String contentType) {
    }
}
