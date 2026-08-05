package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.config.AiProperties;
import com.example.dawanow.entity.ChatPerformanceDirection;
import com.example.dawanow.entity.ChatPerformanceMetric;
import com.example.dawanow.entity.ChatIntent;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GatewayMessage;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GroundedResult;
import com.example.dawanow.service.ai.chat.AiChatModelClient.RouterResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class AiChatModelClientTest {

    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> nextResponse = new AtomicReference<>();
    private final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);
    private final AtomicInteger failuresRemaining = new AtomicInteger(0);

    private HttpServer server;
    private AiChatModelClient client;
    private AiChatProperties chatProperties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/student/chat", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            int status = failuresRemaining.getAndUpdate(count -> Math.max(0, count - 1)) > 0
                    ? 503
                    : nextStatus.get();
            byte[] response = nextResponse.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/api/v1");
        properties.setApiKey("test-sbg-key");
        properties.setGenerationModel("test-model");
        chatProperties = new AiChatProperties();
        chatProperties.setRetryDelay(Duration.ofMillis(10));
        client = new AiChatModelClient(properties, chatProperties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void routeSendsSystemPromptModelAndOrderedMessages() {
        nextResponse.set("{\"output_text\":\"{\\\"intent\\\":\\\"GREETING\\\",\\\"reply\\\":\\\"Hi!\\\","
                + "\\\"searchQuery\\\":\\\"\\\",\\\"doctorSpecializations\\\":[],\\\"emergencyServices\\\":[]}\"}");

        RouterResult result = client.route(
                "system rules",
                List.of(new GatewayMessage("user", "first"),
                        new GatewayMessage("assistant", "second"),
                        new GatewayMessage("user", "hi")),
                300
        );

        assertThat(result.intent()).isEqualTo(ChatIntent.GREETING);
        assertThat(result.reply()).isEqualTo("Hi!");
        assertThat(authHeaders).containsExactly("Bearer test-sbg-key");
        assertThat(requestBodies.getFirst())
                .contains("\"model_id\":\"test-model\"")
                .contains("\"system_prompt\":\"system rules\"")
                .contains("\"max_tokens\":300");
        assertThat(requestBodies.getFirst().indexOf("first"))
                .isLessThan(requestBodies.getFirst().indexOf("second"));
        assertThat(requestBodies.getFirst().indexOf("second"))
                .isLessThan(requestBodies.getFirst().indexOf("hi"));
    }

    @Test
    void routeParsesCodeFencedJson() {
        nextResponse.set("{\"output_text\":\"```json\\n{\\\"intent\\\":\\\"EMERGENCY\\\","
                + "\\\"reply\\\":\\\"Call now\\\",\\\"emergencyServices\\\":[\\\"AMBULANCE\\\",\\\"FIRE\\\"]}\\n```\"}");

        RouterResult result = client.route("system", List.of(new GatewayMessage("user", "help")), 300);

        assertThat(result.intent()).isEqualTo(ChatIntent.EMERGENCY);
        assertThat(result.reply()).isEqualTo("Call now");
        assertThat(result.emergencyServices()).containsExactly("AMBULANCE", "FIRE");
    }

    @Test
    void plainTextResponseFallsBackToOtherIntent() {
        nextResponse.set("{\"output_text\":\"Just some plain text without JSON\"}");

        RouterResult result = client.route("system", List.of(new GatewayMessage("user", "hi")), 300);

        assertThat(result.intent()).isEqualTo(ChatIntent.OTHER);
        assertThat(result.reply()).isEqualTo("Just some plain text without JSON");
    }

    @Test
    void unknownIntentFallsBackToOther() {
        nextResponse.set("{\"output_text\":\"{\\\"intent\\\":\\\"SOMETHING_NEW\\\",\\\"reply\\\":\\\"ok\\\"}\"}");

        RouterResult result = client.route("system", List.of(new GatewayMessage("user", "hi")), 300);

        assertThat(result.intent()).isEqualTo(ChatIntent.OTHER);
        assertThat(result.reply()).isEqualTo("ok");
    }

    @Test
    void routeParsesPharmacistPerformanceFields() {
        nextResponse.set("{\"output_text\":\"{\\\"intent\\\":\\\"PHARMACIST_PERFORMANCE\\\","
                + "\\\"reply\\\":\\\"\\\",\\\"performanceMetric\\\":\\\"BOTH\\\","
                + "\\\"performancePeriod\\\":\\\"LAST_MONTH\\\","
                + "\\\"performanceDirection\\\":\\\"BOTTOM\\\"}\"}");

        RouterResult result = client.route(
                "system", List.of(new GatewayMessage("user", "top staff this month")), 300);

        assertThat(result.intent()).isEqualTo(ChatIntent.PHARMACIST_PERFORMANCE);
        assertThat(result.performanceMetric()).isEqualTo(ChatPerformanceMetric.BOTH);
        assertThat(result.performancePeriod()).isEqualTo(DashboardPeriod.LAST_MONTH);
        assertThat(result.performanceDirection()).isEqualTo(ChatPerformanceDirection.BOTTOM);
    }

    @Test
    void generateGroundedParsesReplyAndProductIds() {
        nextResponse.set("{\"output_text\":\"{\\\"reply\\\":\\\"**Panadol** relieves pain.\\\","
                + "\\\"productIds\\\":[5,9]}\"}");

        GroundedResult result = client.generateGrounded(
                "system", List.of(new GatewayMessage("user", "headache")), 300);

        assertThat(result.reply()).isEqualTo("**Panadol** relieves pain.");
        assertThat(result.productIds()).containsExactly(5L, 9L);
    }

    @Test
    void transientServerErrorsAreRetriedUntilTheGatewayAnswers() {
        nextResponse.set("{\"output_text\":\"{\\\"intent\\\":\\\"GREETING\\\",\\\"reply\\\":\\\"Hi!\\\"}\"}");
        failuresRemaining.set(2);

        RouterResult result = client.route("system", List.of(new GatewayMessage("user", "hi")), 300);

        assertThat(result.intent()).isEqualTo(ChatIntent.GREETING);
        assertThat(requestBodies).hasSize(3);
    }

    @Test
    void serverErrorBecomesServiceUnavailableAfterExhaustingRetries() {
        nextResponse.set("{\"error\":\"boom\"}");
        nextStatus.set(500);

        assertThatThrownBy(() -> client.route("system", List.of(new GatewayMessage("user", "hi")), 300))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(requestBodies).hasSize(chatProperties.getMaxAttempts());
    }

    @Test
    void truncatedJsonStillYieldsReadableTextInsteadOfRawJson() {
        // The model hit the token cap mid-string: no closing quote or brace.
        nextResponse.set("{\"output_text\":\"{\\\"reply\\\":\\\"**Rest first**\\\\nDrink water and sleep\","
                + "\"ignored\":1}");

        GroundedResult result = client.generateGrounded(
                "system", List.of(new GatewayMessage("user", "headache")), 300);

        assertThat(result.reply()).isEqualTo("**Rest first**\nDrink water and sleep");
        assertThat(result.reply()).doesNotContain("{\"reply\"");
    }

    @Test
    void truncatedRouterJsonKeepsIntentAndReply() {
        nextResponse.set("{\"output_text\":\"{\\\"intent\\\":\\\"SYMPTOM_ADVICE\\\","
                + "\\\"reply\\\":\\\"\\\",\\\"searchQuery\\\":\\\"headache\"}");

        RouterResult result = client.route("system", List.of(new GatewayMessage("user", "صداع")), 300);

        assertThat(result.intent()).isEqualTo(ChatIntent.SYMPTOM_ADVICE);
        assertThat(result.searchQuery()).isEqualTo("headache");
    }

    @Test
    void clientErrorsAreNotRetried() {
        nextResponse.set("{\"error\":{\"code\":\"AUTH_INVALID\"}}");
        nextStatus.set(401);

        assertThatThrownBy(() -> client.route("system", List.of(new GatewayMessage("user", "hi")), 300))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(requestBodies).hasSize(1);
    }

    @Test
    void missingApiKeyIsRejectedBeforeCalling() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://localhost:1");
        properties.setApiKey(" ");
        AiChatModelClient unconfigured =
                new AiChatModelClient(properties, new AiChatProperties(), new ObjectMapper());

        assertThatThrownBy(() -> unconfigured.route("system", List.of(new GatewayMessage("user", "hi")), 300))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
