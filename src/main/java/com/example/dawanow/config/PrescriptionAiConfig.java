package com.example.dawanow.config;

import com.example.dawanow.ai.DemoPrescriptionAiClient;
import com.example.dawanow.ai.ItiPrescriptionAiClient;
import com.example.dawanow.ai.PrescriptionAiClient;
import com.example.dawanow.ai.UnavailablePrescriptionAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PrescriptionAiProperties.class)
public class PrescriptionAiConfig {

    @Bean
    PrescriptionAiClient prescriptionAiClient(
            PrescriptionAiProperties properties
    ) {
        return switch (properties.getProvider()) {
            case DEMO -> new DemoPrescriptionAiClient();
            case DISABLED -> new UnavailablePrescriptionAiClient();
            case ITI -> itiClient(properties.getIti());
        };
    }

    private PrescriptionAiClient itiClient(PrescriptionAiProperties.Iti properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        return new ItiPrescriptionAiClient(
                restClient,
                new ObjectMapper(),
                properties.getEndpointUrl(),
                properties.getModel(),
                properties.getApiKey()
        );
    }
}
