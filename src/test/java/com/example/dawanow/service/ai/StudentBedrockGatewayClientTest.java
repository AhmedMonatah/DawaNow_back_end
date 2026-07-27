package com.example.dawanow.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dawanow.config.AiProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class StudentBedrockGatewayClientTest {

    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private StudentBedrockGatewayClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/embed", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));

            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(requestBody);

            byte[] response = embeddingResponse(requestBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        client = new StudentBedrockGatewayClient(cohereProperties(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void embedDocumentsUsesCohereSearchDocumentInputType() {
        List<float[]> vectors = client.embedDocuments(List.of("Panadol tablets", "Insulin pen"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 0.5f);
        assertThat(vectors.get(1)).containsExactly(2.0f, 0.5f);
        assertThat(authHeaders).containsExactly("Bearer test-cohere-key");
        assertThat(requestBodies.getFirst())
                .contains("\"model\":\"embed-multilingual-v3.0\"")
                .contains("\"input_type\":\"search_document\"")
                .contains("\"embedding_types\":[\"float\"]")
                .contains("\"texts\":[\"Panadol tablets\",\"Insulin pen\"]");
    }

    @Test
    void embedQueryUsesCohereSearchQueryInputType() {
        float[] vector = client.embedQuery("paracetamol");

        assertThat(vector).containsExactly(1.0f, 0.5f);
        assertThat(requestBodies.getFirst())
                .contains("\"input_type\":\"search_query\"")
                .contains("\"texts\":[\"paracetamol\"]");
    }

    @Test
    void embedQueryRequiresCohereApiKey() {
        AiProperties properties = cohereProperties();
        properties.setEmbeddingApiKey(" ");
        StudentBedrockGatewayClient missingKeyClient =
                new StudentBedrockGatewayClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> missingKeyClient.embedQuery("paracetamol"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason())
                            .isEqualTo("Cohere API key is not configured; set COHERE_API_KEY");
                });
    }

    private AiProperties cohereProperties() {
        AiProperties properties = new AiProperties();
        properties.setEmbeddingProvider("cohere");
        properties.setEmbeddingBaseUrl("http://localhost:" + server.getAddress().getPort() + "/v2");
        properties.setEmbeddingModel("embed-multilingual-v3.0");
        properties.setEmbeddingApiKey("test-cohere-key");
        return properties;
    }

    private String embeddingResponse(String requestBody) {
        int count = requestBody.contains("Insulin pen") ? 2 : 1;
        StringBuilder values = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                values.append(',');
            }
            values.append('[').append(i).append(".0,0.5]");
        }
        return "{\"embeddings\":{\"float\":[" + values + "]}}";
    }
}
