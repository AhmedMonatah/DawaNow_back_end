package com.example.dawanow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class PrescriptionAiUnavailableException extends RuntimeException {

    private final HttpStatusCode status;

    public PrescriptionAiUnavailableException(String message) {
        this(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public PrescriptionAiUnavailableException(HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
