package com.example.dawanow.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "dawanow.ai")
@Validated
@Getter
@Setter
public class AiProperties {

    @NotNull
    private Duration requestTimeout = Duration.ofMinutes(2);

    @NotBlank
    private String provider = "student-bedrock-gateway";

    private String baseUrl = "";

    private String apiKey = "";

    @NotBlank
    private String embeddingProvider = "cohere";

    @NotBlank
    private String embeddingBaseUrl = "https://api.cohere.com/v2";

    @NotBlank
    private String embeddingModel = "embed-multilingual-v3.0";

    private String embeddingApiKey = "";

    @NotBlank
    private String generationModel = "openai.gpt-oss-20b-1:0";

    @Min(50)
    @Max(2000)
    private int generationMaxTokens = 400;

    @NotNull
    private Retrieval retrieval = new Retrieval();

    @Getter
    @Setter
    public static class Retrieval {

        private boolean initializeOnStartup = true;

        @Min(1)
        @Max(96)
        private int embeddingBatchSize = 32;

        @Min(1)
        @Max(20)
        private int defaultTopK = 8;

        @Min(1)
        @Max(50)
        private int maxTopK = 20;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double semanticWeight = 0.70;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimumScore = 0.20;
    }
}
