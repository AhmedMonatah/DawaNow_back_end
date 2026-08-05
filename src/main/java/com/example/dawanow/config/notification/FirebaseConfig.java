package com.example.dawanow.config.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Initializes the Firebase Admin SDK exactly once at application startup,
 * using the service-account JSON key downloaded from the Firebase console
 * (Project Settings > Service Accounts > Generate new private key).
 *
 * NEVER commit the key file itself — load it via an env-var-driven path
 * or a secrets manager in real deployments. classpath: works for local dev.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials.path:classpath:firebase-service-account.json}")
    private Resource credentialsResource;

    @Value("${FIREBASE_CREDENTIALS_JSON:#{null}}")
    private String firebaseCredentialsJson;

    @PostConstruct
    public void initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount;

                if (org.springframework.util.StringUtils.hasText(firebaseCredentialsJson)) {
                    log.info("Initializing Firebase using FIREBASE_CREDENTIALS_JSON environment variable.");
                    serviceAccount = new java.io.ByteArrayInputStream(firebaseCredentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else if (credentialsResource.exists()) {
                    log.info("Initializing Firebase using credentials file.");
                    serviceAccount = credentialsResource.getInputStream();
                } else {
                    throw new IllegalStateException("Firebase credentials not found! Please set the FIREBASE_CREDENTIALS_JSON environment variable (e.g. in Railway) with the contents of your firebase-service-account.json file.");
                }

                try (serviceAccount) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();
                    FirebaseApp.initializeApp(options);
                    log.info("Firebase Admin SDK initialized");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize Firebase Admin SDK", e);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance(FirebaseApp.getInstance());
    }
}