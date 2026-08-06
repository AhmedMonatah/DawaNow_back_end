package com.example.dawanow.service.ai.chat;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.config.AiProperties;
import com.example.dawanow.entity.ChatPerformanceDirection;
import com.example.dawanow.entity.ChatPerformanceMetric;
import com.example.dawanow.entity.ChatIntent;
import com.example.dawanow.entity.DashboardPeriod;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class AiChatModelClient {

    private final AiProperties properties;
    private final AiChatProperties chatProperties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public AiChatModelClient(
            AiProperties properties,
            AiChatProperties chatProperties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.chatProperties = chatProperties;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(chatProperties.getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getRequestTimeout());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public RouterResult route(String systemPrompt, List<GatewayMessage> messages, int maxTokens) {
        String content = complete("Chat routing", systemPrompt, messages, maxTokens);
        return parseRouterResult(content);
    }

    public GroundedResult generateGrounded(String systemPrompt, List<GatewayMessage> messages, int maxTokens) {
        String content = complete("Chat answer generation", systemPrompt, messages, maxTokens);
        return parseGroundedResult(content);
    }

    /**
     * Plain-text completion for callers that want prose, not the router or
     * grounded JSON contracts (for example the dashboard summary).
     */
    public String generateText(String systemPrompt, List<GatewayMessage> messages, int maxTokens) {
        return complete("Text generation", systemPrompt, messages, maxTokens);
    }

    /** One decision step of the cart agent loop: which tool to call next. */
    public AgentStep agentStep(String systemPrompt, List<GatewayMessage> messages, int maxTokens) {
        String content = complete("Cart agent step", systemPrompt, messages, maxTokens);
        return parseAgentStep(content);
    }

    private AgentStep parseAgentStep(String content) {
        JsonNode payload = tryParseJson(content);
        if (payload == null || !payload.isObject()) {
            return new AgentStep(
                    lenientStringField(content, "tool"),
                    lenientStringField(content, "query"),
                    null,
                    null,
                    lenientStringField(content, "question"),
                    lenientStringField(content, "summary")
            );
        }
        return new AgentStep(
                text(payload, "tool"),
                text(payload, "query"),
                positiveLong(payload, "productId"),
                positiveInt(payload, "quantity"),
                text(payload, "question"),
                text(payload, "summary")
        );
    }

    private Long positiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            return null;
        }
        long number = value.longValue();
        return number > 0 ? number : null;
    }

    private String complete(String capability, String systemPrompt, List<GatewayMessage> messages, int maxTokens) {
        requireStudentGatewayConfigured();

        List<Map<String, Object>> requestMessages = messages.stream()
                .map(message -> Map.<String, Object>of(
                        "role", message.role(),
                        "content", message.content()
                ))
                .toList();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model_id", properties.getGenerationModel());
        request.put("messages", requestMessages);
        request.put("system_prompt", systemPrompt);
        request.put("max_tokens", maxTokens);

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            return extractContent(capability, postWithRetry(capability, requestBody));
        } catch (RestClientException | IllegalArgumentException | JacksonException exception) {
            log.warn("{} request failed: {}", capability, exception.getMessage());
            throw unavailable(capability, exception);
        }
    }

    /**
     * The student gateway regularly drops connections or answers 5xx under load.
     * Those failures are transient, so retry them with a short backoff before
     * telling the caller the assistant is unavailable.
     */
    private String postWithRetry(String capability, String requestBody) {
        URI endpoint = studentGatewayEndpoint("/student/chat");
        int maxAttempts = chatProperties.getMaxAttempts();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return client.post()
                        .uri(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .contentLength(requestBody.getBytes(StandardCharsets.UTF_8).length)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
            } catch (ResourceAccessException exception) {
                lastFailure = exception;
                log.warn("{} attempt {}/{} failed to reach the gateway: {}",
                        capability, attempt, maxAttempts, exception.getMessage());
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                int status = exception.getStatusCode().value();
                if (status != 429 && status < 500) {
                    throw exception;
                }
                log.warn("{} attempt {}/{} failed with HTTP {}", capability, attempt, maxAttempts, status);
            }

            if (attempt < maxAttempts) {
                sleepBeforeRetry(attempt);
            }
        }
        throw lastFailure;
    }

    private void sleepBeforeRetry(int attempt) {
        Duration delay = chatProperties.getRetryDelay().multipliedBy(attempt);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying the AI gateway", exception);
        }
    }

    private String extractContent(String capability, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw unavailable(capability, null);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.isObject() && (root.get("intent") != null || root.get("reply") != null)) {
                return responseBody;
            }
            String content = extractText(root);
            if (content == null || content.isBlank()) {
                throw unavailable(capability, null);
            }
            return content;
        } catch (JacksonException exception) {
            throw unavailable(capability, exception);
        }
    }

    private RouterResult parseRouterResult(String content) {
        JsonNode payload = tryParseJson(content);
        if (payload == null || !payload.isObject()) {
            // A truncated answer is still usable: recover the fields we can read.
            return new RouterResult(
                    parseIntent(lenientStringField(content, "intent")),
                    firstNonBlank(lenientStringField(content, "reply"), content.trim()),
                    lenientStringField(content, "searchQuery"),
                    List.of(),
                    List.of(),
                    null,
                    lenientReminderSpec(content),
                    List.of(),
                    parsePerformanceMetric(lenientStringField(content, "performanceMetric")),
                    parseDashboardPeriod(lenientStringField(content, "performancePeriod")),
                    parsePerformanceDirection(lenientStringField(content, "performanceDirection"))
            );
        }
        return new RouterResult(
                parseIntent(text(payload, "intent")),
                text(payload, "reply"),
                text(payload, "searchQuery"),
                textList(payload, "doctorSpecializations"),
                textList(payload, "emergencyServices"),
                positiveInt(payload, "quantity"),
                reminderSpec(payload),
                textList(payload, "categoryNames"),
                parsePerformanceMetric(text(payload, "performanceMetric")),
                parseDashboardPeriod(text(payload, "performancePeriod")),
                parsePerformanceDirection(text(payload, "performanceDirection"))
        );
    }

    /**
     * Assembles the nested reminder record from the flat JSON keys the router
     * emits — the small model fills flat fields far more reliably than nested
     * objects, and zero/empty doubles as its "unstated" sentinel.
     */
    private ReminderSpec reminderSpec(JsonNode payload) {
        String medicine = text(payload, "reminderMedicine");
        Integer timesPerDay = positiveInt(payload, "reminderTimesPerDay");
        List<String> times = textList(payload, "reminderTimes");
        Integer durationDays = positiveInt(payload, "reminderDurationDays");
        if ((medicine == null || medicine.isBlank())
                && timesPerDay == null && times.isEmpty() && durationDays == null) {
            return null;
        }
        return new ReminderSpec(medicine, timesPerDay, times, durationDays);
    }

    private ReminderSpec lenientReminderSpec(String content) {
        String medicine = lenientStringField(content, "reminderMedicine");
        return medicine == null ? null : new ReminderSpec(medicine, null, List.of(), null);
    }

    private Integer positiveInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            return null;
        }
        int number = value.intValue();
        return number > 0 ? number : null;
    }

    private GroundedResult parseGroundedResult(String content) {
        JsonNode payload = tryParseJson(content);
        if (payload == null || !payload.isObject()) {
            String recovered = firstNonBlank(
                    lenientStringField(content, "reply"),
                    lenientStringField(content, "answer")
            );
            return new GroundedResult(
                    recovered != null ? recovered : content.trim(),
                    lenientProductIds(content)
            );
        }
        String reply = text(payload, "reply");
        if (reply == null || reply.isBlank()) {
            reply = text(payload, "answer");
        }
        return new GroundedResult(
                reply == null ? content.trim() : reply,
                idList(payload)
        );
    }

    /**
     * Reads one string field out of a JSON payload that may be truncated, so a
     * cut-off answer still reaches the user as text instead of raw JSON.
     */
    private String lenientStringField(String content, String field) {
        int keyIndex = content.indexOf("\"" + field + "\"");
        if (keyIndex < 0) {
            return null;
        }
        int cursor = content.indexOf(':', keyIndex);
        if (cursor < 0) {
            return null;
        }
        cursor++;
        while (cursor < content.length() && Character.isWhitespace(content.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= content.length() || content.charAt(cursor) != '"') {
            return null;
        }

        StringBuilder value = new StringBuilder();
        for (cursor++; cursor < content.length(); cursor++) {
            char character = content.charAt(cursor);
            if (character == '"') {
                break;
            }
            if (character != '\\' || cursor + 1 >= content.length()) {
                value.append(character);
                continue;
            }
            char escaped = content.charAt(++cursor);
            switch (escaped) {
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (cursor + 4 < content.length()) {
                        try {
                            value.append((char) Integer.parseInt(content.substring(cursor + 1, cursor + 5), 16));
                            cursor += 4;
                        } catch (NumberFormatException ignored) {
                            value.append(escaped);
                        }
                    }
                }
                default -> value.append(escaped);
            }
        }
        String recovered = value.toString().trim();
        return recovered.isEmpty() ? null : recovered;
    }

    private List<Long> lenientProductIds(String content) {
        int keyIndex = content.indexOf("\"productIds\"");
        if (keyIndex < 0) {
            return List.of();
        }
        int start = content.indexOf('[', keyIndex);
        if (start < 0) {
            return List.of();
        }
        int end = content.indexOf(']', start);
        String body = end < 0 ? content.substring(start + 1) : content.substring(start + 1, end);

        List<Long> ids = new ArrayList<>();
        for (String token : body.split(",")) {
            try {
                ids.add(Long.parseLong(token.trim()));
            } catch (NumberFormatException ignored) {
                // Ignore partial or malformed trailing entries.
            }
        }
        return List.copyOf(ids);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode tryParseJson(String content) {
        try {
            return objectMapper.readTree(stripCodeFence(content));
        } catch (JacksonException exception) {
            // Some approved models may return plain text despite the JSON instruction.
            return null;
        }
    }

    private ChatIntent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return ChatIntent.OTHER;
        }
        try {
            return ChatIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ChatIntent.OTHER;
        }
    }

    private ChatPerformanceMetric parsePerformanceMetric(String value) {
        return parseEnum(value, ChatPerformanceMetric.class);
    }

    private DashboardPeriod parseDashboardPeriod(String value) {
        return parseEnum(value, DashboardPeriod.class);
    }

    private ChatPerformanceDirection parsePerformanceDirection(String value) {
        return parseEnum(value, ChatPerformanceDirection.class);
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.asString().trim() : null;
    }

    private List<String> textList(JsonNode node, String field) {
        JsonNode values = node.get(field);
        if (values == null || !values.isArray()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode value : values) {
            if (value.isString() && !value.asString().isBlank()) {
                items.add(value.asString().trim());
            }
        }
        return List.copyOf(items);
    }

    private List<Long> idList(JsonNode node) {
        JsonNode values = node.get("productIds");
        if (values == null) {
            values = node.get("product_ids");
        }
        if (values == null || !values.isArray()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode value : values) {
            if (value.isIntegralNumber()) {
                ids.add(value.longValue());
            }
        }
        return List.copyOf(ids);
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

        JsonNode choices = node.get("choices");
        if (choices != null && choices.isArray()) {
            for (JsonNode choice : choices) {
                String text = extractText(choice);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
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
        String baseUrl = properties.getBaseUrl().trim().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl + path);
        if (!uri.isAbsolute() || uri.getScheme() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("SBG_BASE_URL must be an absolute HTTP or HTTPS URL");
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

    private ResponseStatusException unavailable(String capability, Exception cause) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                capability + " is unavailable from provider '" + properties.getProvider()
                        + "' using model '" + properties.getGenerationModel() + "'",
                cause
        );
    }

    public record GatewayMessage(String role, String content) {
    }

    public record RouterResult(
            ChatIntent intent,
            String reply,
            String searchQuery,
            List<String> doctorSpecializations,
            List<String> emergencyServices,
            Integer quantity,
            ReminderSpec reminder,
            List<String> categoryNames,
            ChatPerformanceMetric performanceMetric,
            DashboardPeriod performancePeriod,
            ChatPerformanceDirection performanceDirection
    ) {

        public RouterResult(
                ChatIntent intent,
                String reply,
                String searchQuery,
                List<String> doctorSpecializations,
                List<String> emergencyServices,
                Integer quantity,
                ReminderSpec reminder,
                List<String> categoryNames,
                ChatPerformanceMetric performanceMetric,
                DashboardPeriod performancePeriod
        ) {
            this(intent, reply, searchQuery, doctorSpecializations, emergencyServices,
                    quantity, reminder, categoryNames, performanceMetric, performancePeriod, null);
        }

        /** Convenience for the common intents that carry no cart, reminder or category data. */
        public RouterResult(
                ChatIntent intent,
                String reply,
                String searchQuery,
                List<String> doctorSpecializations,
                List<String> emergencyServices
        ) {
            this(intent, reply, searchQuery, doctorSpecializations, emergencyServices,
                    null, null, List.of(), null, null, null);
        }

        /** Convenience for cart/reminder intents without category data. */
        public RouterResult(
                ChatIntent intent,
                String reply,
                String searchQuery,
                List<String> doctorSpecializations,
                List<String> emergencyServices,
                Integer quantity,
                ReminderSpec reminder
        ) {
            this(intent, reply, searchQuery, doctorSpecializations, emergencyServices,
                    quantity, reminder, List.of(), null, null, null);
        }

        /** Convenience for category intents without pharmacist-performance data. */
        public RouterResult(
                ChatIntent intent,
                String reply,
                String searchQuery,
                List<String> doctorSpecializations,
                List<String> emergencyServices,
                Integer quantity,
                ReminderSpec reminder,
                List<String> categoryNames
        ) {
            this(intent, reply, searchQuery, doctorSpecializations, emergencyServices,
                    quantity, reminder, categoryNames, null, null);
        }
    }

    /** Reminder details extracted by the router; null fields mean "unstated". */
    public record ReminderSpec(
            String medicine,
            Integer timesPerDay,
            List<String> times,
            Integer durationDays
    ) {
    }

    public record GroundedResult(String reply, List<Long> productIds) {
    }

    /**
     * One tool call chosen by the cart agent. Exactly one tool per step; the
     * other fields are only meaningful for the tool that uses them.
     */
    public record AgentStep(
            String tool,
            String query,
            Long productId,
            Integer quantity,
            String question,
            String summary
    ) {
    }
}
