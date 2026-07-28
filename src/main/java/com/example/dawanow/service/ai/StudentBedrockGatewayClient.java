package com.example.dawanow.service.ai;

import com.example.dawanow.config.AiProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class StudentBedrockGatewayClient {

    private static final String SYSTEM_PROMPT = """
            You are DawaNow's medicine catalog lookup assistant, not a clinician.

            Use only the supplied product context. You may state catalog facts: product name, strength, pack size,
            form, scientific name and category, consumer category, company, route, description, and listed price.
            Never invent stock or availability.

            Do not diagnose, prescribe, recommend what the user should take, provide dosage, assess interactions or
            contraindications, or declare products interchangeable. If clinical judgment is required, say that a
            pharmacist or doctor must answer it. If the context does not contain the answer, say it was not found.

            Return only compact JSON in this exact shape:
            {"answer":"your answer","productIds":[1,2]}
            Include only product IDs present in the supplied context and keep the answer under 180 words.
            """;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public StudentBedrockGatewayClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getRequestTimeout());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, "search_document");
    }

    public float[] embedQuery(String query) {
        return embed(List.of(query), "search_query").getFirst();
    }

    private List<float[]> embed(List<String> texts, String inputType) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        if ("cohere".equalsIgnoreCase(properties.getEmbeddingProvider())) {
            return embedWithCohere(texts, inputType);
        }
        if ("ollama".equalsIgnoreCase(properties.getEmbeddingProvider())) {
            return embedWithOllama(texts, inputType);
        }
        if ("student-bedrock-gateway".equalsIgnoreCase(properties.getEmbeddingProvider())) {
            return embedWithStudentGateway(texts, inputType);
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Unsupported embedding provider: " + properties.getEmbeddingProvider()
        );
    }

    private static final int COHERE_MAX_TEXTS_PER_REQUEST = 32;
    private static final int COHERE_MAX_TEXT_CHARS = 800;
    private static final int COHERE_MAX_ATTEMPTS = 10;
    private static final long COHERE_TRIAL_TPM_LIMIT = 90_000L;

    private List<float[]> embedWithCohere(List<String> texts, String inputType) {
        requireCohereConfigured();

        List<float[]> embeddings = new ArrayList<>(texts.size());
        try {
            for (int start = 0; start < texts.size(); start += COHERE_MAX_TEXTS_PER_REQUEST) {
                if (start > 0) {
                    // Trial keys are limited to ~100k tokens/minute; pace between chunked calls.
                    Thread.sleep(Duration.ofSeconds(2));
                }
                List<String> batch = texts.subList(
                        start,
                        Math.min(start + COHERE_MAX_TEXTS_PER_REQUEST, texts.size())
                );
                embeddings.addAll(embedCohereBatch(batch, inputType));
            }
            return List.copyOf(embeddings);
        } catch (RestClientException | IllegalArgumentException | JacksonException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn(
                    "Cohere embed failed for {} text(s): {}",
                    texts.size(),
                    summarizeHttpError(exception)
            );
            throw unavailable("Text embedding", properties.getEmbeddingModel(), exception);
        }
    }

    private List<float[]> embedCohereBatch(List<String> texts, String inputType)
            throws JacksonException, InterruptedException {
        List<String> truncated = texts.stream()
                .map(this::truncateForCohere)
                .toList();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getEmbeddingModel());
        request.put("texts", truncated);
        request.put("input_type", inputType);
        request.put("embedding_types", List.of("float"));

        String requestBody = objectMapper.writeValueAsString(request);
        String response = postCohere("/embed", requestBody);
        return parseEmbeddings(response, texts.size());
    }

    private String postCohere(String path, String requestBody) throws InterruptedException {
        URI endpoint = cohereEndpoint(path);
        RestClientException lastException = null;

        for (int attempt = 1; attempt <= COHERE_MAX_ATTEMPTS; attempt++) {
            try {
                return client.post()
                        .uri(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getEmbeddingApiKey().trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
            } catch (org.springframework.web.client.RestClientResponseException exception) {
                lastException = exception;
                int status = exception.getStatusCode().value();
                if ((status != 429 && status != 500 && status != 503) || attempt == COHERE_MAX_ATTEMPTS) {
                    throw exception;
                }
                Duration delay = cohereRetryDelay(exception, attempt);
                log.warn(
                        "Cohere embed rate-limited/unavailable (HTTP {}), retrying in {} (attempt {}/{})",
                        status,
                        delay,
                        attempt,
                        COHERE_MAX_ATTEMPTS
                );
                Thread.sleep(delay);
            }
        }
        throw lastException;
    }

    private Duration cohereRetryDelay(
            org.springframework.web.client.RestClientResponseException exception,
            int attempt
    ) {
        String body = exception.getResponseBodyAsString();
        if (body != null && body.toLowerCase().contains("token")) {
            // Trial TPM windows are per minute; wait out the full window.
            return Duration.ofSeconds(65);
        }
        return Duration.ofSeconds(Math.min(30, 3L * attempt));
    }

    public long estimateCohereTokens(List<String> texts) {
        long chars = 0;
        for (String text : texts) {
            chars += truncateForCohere(text).length();
        }
        // Mixed Arabic/English is denser than English-only; bias high.
        return Math.max(1L, (chars + 2) / 3);
    }

    public Duration coherePaceDelay(List<String> texts) {
        long tokens = estimateCohereTokens(texts);
        long millis = Math.max(1_000L, (tokens * 60_000L) / COHERE_TRIAL_TPM_LIMIT);
        return Duration.ofMillis(millis);
    }

    private String truncateForCohere(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        if (cleaned.length() <= COHERE_MAX_TEXT_CHARS) {
            return cleaned;
        }
        return cleaned.substring(0, COHERE_MAX_TEXT_CHARS);
    }

    private String summarizeHttpError(Exception exception) {
        if (exception instanceof org.springframework.web.client.RestClientResponseException responseException) {
            String body = responseException.getResponseBodyAsString();
            if (body == null) {
                body = "";
            }
            String cleaned = body.replaceAll("\\s+", " ").trim();
            if (cleaned.length() > 400) {
                cleaned = cleaned.substring(0, 400) + "...";
            }
            return "HTTP " + responseException.getStatusCode().value() + " body=" + cleaned;
        }
        return exception.getMessage();
    }

    private List<float[]> embedWithOllama(List<String> texts, String inputType) {
        List<String> prefixedTexts = texts.stream()
                .map(text -> inputType + ": " + text)
                .toList();
        Map<String, Object> request = Map.of(
                "model", properties.getEmbeddingModel(),
                "input", prefixedTexts
        );

        try {
            String response = client.post()
                    .uri(ollamaEndpoint("/api/embed"))
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return parseEmbeddings(response, texts.size());
        } catch (RestClientException | IllegalArgumentException exception) {
            throw unavailable("Text embedding", properties.getEmbeddingModel(), exception);
        }
    }

    private List<float[]> embedWithStudentGateway(List<String> texts, String inputType) {
        requireStudentGatewayConfigured();

        Map<String, Object> request = Map.of(
                "model_id", properties.getEmbeddingModel(),
                "texts", texts,
                "input_type", inputType
        );

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            String response = client.post()
                    .uri(studentGatewayEndpoint("/student/embed"))
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.getBytes(StandardCharsets.UTF_8).length)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseEmbeddings(response, texts.size());
        } catch (RestClientException | IllegalArgumentException | JacksonException exception) {
            throw unavailable("Text embedding", properties.getEmbeddingModel(), exception);
        }
    }

    public GeneratedAnswer generate(String question, String language, String productContext) {
        requireStudentGatewayConfigured();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model_id", properties.getGenerationModel());
        request.put("messages", List.of(Map.of(
                "role", "user",
                "content", "Answer language: " + ("ar".equals(language) ? "Arabic" : "English")
                        + "\n\nQuestion:\n" + question
                        + "\n\nRetrieved product context:\n" + productContext
        )));
        request.put("system_prompt", SYSTEM_PROMPT);
        request.put("max_tokens", properties.getGenerationMaxTokens());

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            String response = client.post()
                    .uri(studentGatewayEndpoint("/student/chat"))
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.getBytes(StandardCharsets.UTF_8).length)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseGeneratedAnswer(response);
        } catch (RestClientException | IllegalArgumentException | JacksonException exception) {
            log.warn("Catalog answer generation request failed: {}", exception.getMessage());
            throw unavailable("Catalog answer generation", properties.getGenerationModel(), exception);
        }
    }

    public String embeddingProvider() {
        return properties.getEmbeddingProvider();
    }

    public String embeddingModel() {
        return properties.getEmbeddingModel();
    }

    public String generationProvider() {
        return properties.getProvider();
    }

    public String generationModel() {
        return properties.getGenerationModel();
    }

    private List<float[]> parseEmbeddings(String responseBody, int expectedCount) {
        try {
            JsonNode root = requiredJson(responseBody, "Text embedding");
            JsonNode values = findEmbeddingValues(root);
            List<float[]> embeddings = vectors(values);
            if (embeddings.size() != expectedCount) {
                throw unavailable("Text embedding", properties.getEmbeddingModel(), null);
            }

            int dimensions = embeddings.getFirst().length;
            for (float[] embedding : embeddings) {
                if (embedding.length == 0 || embedding.length != dimensions) {
                    throw unavailable("Text embedding", properties.getEmbeddingModel(), null);
                }
                double magnitude = 0;
                for (float component : embedding) {
                    if (!Float.isFinite(component)) {
                        throw unavailable("Text embedding", properties.getEmbeddingModel(), null);
                    }
                    magnitude += component * component;
                }
                if (magnitude == 0) {
                    throw unavailable("Text embedding", properties.getEmbeddingModel(), null);
                }
            }
            return List.copyOf(embeddings);
        } catch (JacksonException exception) {
            throw unavailable("Text embedding", properties.getEmbeddingModel(), exception);
        }
    }

    private JsonNode findEmbeddingValues(JsonNode root) {
        for (String field : List.of("embeddings", "vectors", "embedding")) {
            JsonNode candidate = root.get(field);
            if (candidate != null) {
                return unwrapEmbeddingType(candidate);
            }
        }

        for (String field : List.of("data", "result")) {
            JsonNode nested = root.get(field);
            if (nested != null && nested.isObject()) {
                return findEmbeddingValues(nested);
            }
            if (nested != null && nested.isArray()) {
                return nested;
            }
        }
        throw unavailable("Text embedding", properties.getEmbeddingModel(), null);
    }

    private JsonNode unwrapEmbeddingType(JsonNode node) {
        if (!node.isObject()) {
            return node;
        }
        for (String field : List.of("float", "floats", "embeddings", "values")) {
            JsonNode candidate = node.get(field);
            if (candidate != null) {
                return candidate;
            }
        }
        return node;
    }

    private List<float[]> vectors(JsonNode node) {
        if (node == null || !node.isArray() || node.size() == 0) {
            throw unavailable("Text embedding", properties.getEmbeddingModel(), null);
        }

        JsonNode first = node.get(0);
        if (first.isNumber()) {
            return List.of(vector(node));
        }

        List<float[]> vectors = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            JsonNode vectorNode = item;
            if (item.isObject()) {
                vectorNode = item.get("embedding");
                if (vectorNode == null) {
                    vectorNode = item.get("vector");
                }
                if (vectorNode == null) {
                    vectorNode = item.get("values");
                }
                if (vectorNode != null) {
                    vectorNode = unwrapEmbeddingType(vectorNode);
                }
            }
            vectors.add(vector(vectorNode));
        }
        return vectors;
    }

    private float[] vector(JsonNode node) {
        if (node == null || !node.isArray() || node.size() == 0) {
            throw unavailable("Text embedding", properties.getEmbeddingModel(), null);
        }
        float[] vector = new float[node.size()];
        for (int index = 0; index < node.size(); index++) {
            JsonNode component = node.get(index);
            if (component == null || !component.isNumber()) {
                throw unavailable("Text embedding", properties.getEmbeddingModel(), null);
            }
            vector[index] = component.floatValue();
        }
        return vector;
    }

    private GeneratedAnswer parseGeneratedAnswer(String responseBody) {
        try {
            JsonNode root = requiredJson(responseBody, "Catalog answer generation");
            AnswerPayload direct = answerPayload(root);
            if (direct != null) {
                return direct.toGeneratedAnswer();
            }

            String content = extractText(root);
            if (content == null || content.isBlank()) {
                throw unavailable("Catalog answer generation", properties.getGenerationModel(), null);
            }

            String cleaned = stripCodeFence(content);
            try {
                AnswerPayload payload = answerPayload(objectMapper.readTree(cleaned));
                if (payload != null) {
                    return payload.toGeneratedAnswer();
                }
            } catch (JacksonException ignored) {
                // Some approved models may return plain text despite the JSON instruction.
            }
            return new GeneratedAnswer(cleaned.trim(), List.of());
        } catch (JacksonException exception) {
            throw unavailable("Catalog answer generation", properties.getGenerationModel(), exception);
        }
    }

    private AnswerPayload answerPayload(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode answerNode = node.get("answer");
        if (answerNode == null || !answerNode.isString() || answerNode.asString().isBlank()) {
            return null;
        }

        List<Long> productIds = new ArrayList<>();
        JsonNode idsNode = node.get("productIds");
        if (idsNode == null) {
            idsNode = node.get("product_ids");
        }
        if (idsNode != null && idsNode.isArray()) {
            for (JsonNode id : idsNode) {
                if (id.isIntegralNumber()) {
                    productIds.add(id.longValue());
                }
            }
        }
        return new AnswerPayload(answerNode.asString().trim(), List.copyOf(productIds));
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isString()) {
            return node.asString();
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = extractText(item);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
            return null;
        }

        for (String field : List.of("content", "output_text", "response", "text", "completion")) {
            String value = extractText(node.get(field));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        for (String field : List.of("message", "output", "result", "data")) {
            String nestedText = extractText(node.get(field));
            if (nestedText != null && !nestedText.isBlank()) {
                return nestedText;
            }
        }

        for (String field : List.of("choices")) {
            JsonNode values = node.get(field);
            if (values != null && values.isArray()) {
                for (JsonNode value : values) {
                    String text = extractText(value);
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private JsonNode requiredJson(String responseBody, String capability) throws JacksonException {
        if (responseBody == null || responseBody.isBlank()) {
            throw unavailable(capability, modelFor(capability), null);
        }
        return objectMapper.readTree(responseBody);
    }

    private String stripCodeFence(String content) {
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            int firstLineEnd = cleaned.indexOf('\n');
            if (firstLineEnd >= 0) {
                cleaned = cleaned.substring(firstLineEnd + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        int objectStart = cleaned.indexOf('{');
        int objectEnd = cleaned.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return cleaned.substring(objectStart, objectEnd + 1).trim();
        }
        return cleaned.trim();
    }

    private URI studentGatewayEndpoint(String path) {
        return endpoint(properties.getBaseUrl(), path, "SBG_BASE_URL");
    }

    private URI ollamaEndpoint(String path) {
        return endpoint(properties.getEmbeddingBaseUrl(), path, "AI_EMBEDDING_BASE_URL");
    }

    private URI cohereEndpoint(String path) {
        return endpoint(properties.getEmbeddingBaseUrl(), path, "AI_EMBEDDING_BASE_URL");
    }

    private URI endpoint(String configuredBaseUrl, String path, String settingName) {
        String baseUrl = configuredBaseUrl.trim().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl + path);
        if (!uri.isAbsolute() || uri.getScheme() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(settingName + " must be an absolute HTTP or HTTPS URL");
        }
        return uri;
    }

    private String bearerToken() {
        return "Bearer " + properties.getApiKey().trim();
    }

    private void requireStudentGatewayConfigured() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Student Bedrock Gateway API key is not configured; set SBG_API_KEY"
            );
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Student Bedrock Gateway URL is not configured; set SBG_BASE_URL"
            );
        }
    }

    private void requireCohereConfigured() {
        if (properties.getEmbeddingApiKey() == null || properties.getEmbeddingApiKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cohere API key is not configured; set COHERE_API_KEY"
            );
        }
    }

    private String modelFor(String capability) {
        return capability.startsWith("Text embedding")
                ? properties.getEmbeddingModel()
                : properties.getGenerationModel();
    }

    private ResponseStatusException unavailable(String capability, String model, Exception cause) {
        String provider = capability.startsWith("Text embedding")
                ? properties.getEmbeddingProvider()
                : properties.getProvider();
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                capability + " is unavailable from provider '" + provider
                        + "' using model '" + model + "'",
                cause
        );
    }

    public record GeneratedAnswer(String answer, List<Long> productIds) {
    }

    private record AnswerPayload(String answer, List<Long> productIds) {
        GeneratedAnswer toGeneratedAnswer() {
            return new GeneratedAnswer(answer, productIds);
        }
    }
}
