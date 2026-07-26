package com.example.dawanow.service.ai.rag;

import com.example.dawanow.config.AiProperties;
import com.example.dawanow.controller.CatalogAiController.CatalogAnswerResponse;
import com.example.dawanow.controller.CatalogAiController.CatalogIndexStatusResponse;
import com.example.dawanow.controller.CatalogAiController.CatalogQuestionRequest;
import com.example.dawanow.controller.CatalogAiController.CatalogSearchResponse;
import com.example.dawanow.controller.CatalogAiController.ProductMatchResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.ProductEmbedding;
import com.example.dawanow.entity.ProductTranslation;
import com.example.dawanow.repo.ProductEmbeddingRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.service.ai.StudentBedrockGatewayClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@Order(100)
public class CatalogRagService implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final ProductEmbeddingRepository embeddingRepository;
    private final StudentBedrockGatewayClient aiClient;
    private final AiProperties properties;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    private volatile Instant lastRefresh;
    private volatile String lastError;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getRetrieval().isInitializeOnStartup()) {
            refresh();
        }
    }

    public synchronized CatalogIndexStatusResponse refresh() {
        List<CatalogDocument> documents = productRepository.findAllForRetrieval().stream()
                .map(this::toDocument)
                .toList();
        Map<Long, ProductEmbedding> stored = embeddingRepository.findAll().stream()
                .collect(Collectors.toMap(ProductEmbedding::getProductId, Function.identity()));
        Map<Long, float[]> vectors = new HashMap<>();
        List<CatalogDocument> missing = new ArrayList<>();

        for (CatalogDocument document : documents) {
            ProductEmbedding cached = stored.get(document.productId());
            if (isCurrent(cached, document)) {
                try {
                    vectors.put(document.productId(), decode(cached.getEmbedding(), cached.getDimensions()));
                    continue;
                } catch (IllegalArgumentException exception) {
                    log.warn("Ignoring invalid stored embedding for product {}", document.productId());
                }
            }
            missing.add(document);
        }

        lastError = null;
        if (!missing.isEmpty()) {
            try {
                embedMissing(missing, vectors);
            } catch (RuntimeException exception) {
                Throwable root = exception;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                lastError = exception.getMessage();
                log.warn(
                        "Catalog semantic index is degraded: {} (cause: {})",
                        exception.getMessage(),
                        root.getMessage()
                );
            }
        }

        removeDeletedEmbeddings(documents, stored.keySet());
        snapshot.set(new Snapshot(List.copyOf(documents), Map.copyOf(vectors)));
        lastRefresh = Instant.now();
        return status();
    }

    public CatalogSearchResponse search(String query, String language, Integer requestedLimit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Catalog search query is required");
        }
        if (query.length() > 500) {
            throw new IllegalArgumentException("Catalog search query must not exceed 500 characters");
        }

        String lang = normalizeLanguage(language);
        int limit = normalizeLimit(requestedLimit);
        String normalizedQuery = normalizeText(query);
        if (normalizedQuery.isBlank()) {
            throw new IllegalArgumentException("Catalog search query must contain letters or numbers");
        }

        Snapshot current = currentSnapshot();
        float[] queryVector = null;
        if (!current.vectors().isEmpty()) {
            try {
                queryVector = aiClient.embedQuery(query.trim());
            } catch (RuntimeException exception) {
                log.warn("Semantic query embedding failed; using lexical search: {}", exception.getMessage());
            }
        }
        boolean semanticUsed = queryVector != null;
        final float[] semanticQuery = queryVector;

        List<ProductMatchResponse> matches = current.documents().stream()
                .map(document -> score(document, normalizedQuery, semanticQuery, current, lang))
                .filter(match -> match.lexicalScore() >= 0.55
                        || match.score() >= properties.getRetrieval().getMinimumScore())
                .sorted(Comparator.comparingDouble(ProductMatchResponse::score).reversed()
                        .thenComparing(match -> match.product().name()))
                .limit(limit)
                .toList();

        return new CatalogSearchResponse(
                query.trim(),
                lang,
                semanticUsed,
                aiClient.embeddingProvider(),
                aiClient.embeddingModel(),
                matches
        );
    }

    public CatalogAnswerResponse answer(CatalogQuestionRequest request) {
        String language = normalizeLanguage(request.lang());
        CatalogSearchResponse search = search(request.question(), language, request.limit());
        if (search.matches().isEmpty()) {
            return new CatalogAnswerResponse(
                    request.question().trim(),
                    "ar".equals(language)
                            ? "\u0644\u0645 \u064a\u062a\u0645 \u0627\u0644\u0639\u062b\u0648\u0631 \u0639\u0644\u0649 \u0645\u0639\u0644\u0648\u0645\u0627\u062a \u0645\u0646\u0627\u0633\u0628\u0629 \u0641\u064a \u0643\u062a\u0627\u0644\u0648\u062c \u0627\u0644\u0623\u062f\u0648\u064a\u0629."
                            : "No relevant information was found in the medicine catalog.",
                    language,
                    aiClient.generationProvider(),
                    aiClient.generationModel(),
                    search.semanticSearchUsed(),
                    List.of()
            );
        }

        StudentBedrockGatewayClient.GeneratedAnswer generated = aiClient.generate(
                request.question().trim(),
                language,
                buildContext(search.matches())
        );
        Set<Long> allowedIds = search.matches().stream()
                .map(match -> match.product().id())
                .collect(Collectors.toSet());
        Set<Long> citedIds = new HashSet<>(generated.productIds());
        citedIds.retainAll(allowedIds);
        List<ProductMatchResponse> sources = citedIds.isEmpty()
                ? search.matches()
                : search.matches().stream()
                        .filter(match -> citedIds.contains(match.product().id()))
                        .toList();

        return new CatalogAnswerResponse(
                request.question().trim(),
                generated.answer(),
                language,
                aiClient.generationProvider(),
                aiClient.generationModel(),
                search.semanticSearchUsed(),
                sources
        );
    }

    public CatalogIndexStatusResponse status() {
        Snapshot current = snapshot.get();
        return new CatalogIndexStatusResponse(
                current.documents().size(),
                current.vectors().size(),
                !current.vectors().isEmpty(),
                aiClient.embeddingProvider(),
                aiClient.embeddingModel(),
                lastRefresh,
                lastError
        );
    }

    private Snapshot currentSnapshot() {
        Snapshot current = snapshot.get();
        if (current.documents().isEmpty() && productRepository.count() > 0) {
            refresh();
            return snapshot.get();
        }
        return current;
    }

    private void embedMissing(List<CatalogDocument> missing, Map<Long, float[]> vectors) {
        int batchSize = Math.min(32, properties.getRetrieval().getEmbeddingBatchSize());
        boolean paceForCohere = "cohere".equalsIgnoreCase(aiClient.embeddingProvider());
        for (int start = 0; start < missing.size(); start += batchSize) {
            List<CatalogDocument> batch = missing.subList(start, Math.min(start + batchSize, missing.size()));
            List<String> texts = batch.stream().map(CatalogDocument::embeddingText).toList();
            if (paceForCohere && start > 0) {
                Duration pause = aiClient.coherePaceDelay(texts);
                log.info("Pacing Cohere embeddings for {} to stay under trial token limits", pause);
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while pacing Cohere embeddings", exception);
                }
            }
            log.info(
                    "Embedding catalog batch {}-{} of {}",
                    start + 1,
                    start + batch.size(),
                    missing.size()
            );
            List<float[]> generated = aiClient.embedDocuments(texts);
            List<ProductEmbedding> entities = new ArrayList<>(batch.size());
            for (int index = 0; index < batch.size(); index++) {
                CatalogDocument document = batch.get(index);
                float[] vector = generated.get(index);
                vectors.put(document.productId(), vector);
                entities.add(toEntity(document, vector));
            }
            embeddingRepository.saveAll(entities);
        }
    }

    private ProductEmbedding toEntity(CatalogDocument document, float[] vector) {
        ProductEmbedding embedding = new ProductEmbedding();
        embedding.setProductId(document.productId());
        embedding.setProvider(aiClient.embeddingProvider());
        embedding.setModel(aiClient.embeddingModel());
        embedding.setContentHash(document.contentHash());
        embedding.setDimensions(vector.length);
        embedding.setEmbedding(encode(vector));
        embedding.setEmbeddedAt(Instant.now());
        return embedding;
    }

    private boolean isCurrent(ProductEmbedding embedding, CatalogDocument document) {
        return embedding != null
                && aiClient.embeddingProvider().equalsIgnoreCase(embedding.getProvider())
                && aiClient.embeddingModel().equals(embedding.getModel())
                && document.contentHash().equals(embedding.getContentHash());
    }

    private void removeDeletedEmbeddings(List<CatalogDocument> documents, Set<Long> storedIds) {
        Set<Long> currentIds = documents.stream()
                .map(CatalogDocument::productId)
                .collect(Collectors.toCollection(HashSet::new));
        List<Long> deletedIds = storedIds.stream().filter(id -> !currentIds.contains(id)).toList();
        if (!deletedIds.isEmpty()) {
            embeddingRepository.deleteAllByIdInBatch(deletedIds);
        }
    }

    private CatalogDocument toDocument(Product product) {
        ProductTranslation translation = product.getTranslations().stream()
                .filter(item -> "ar".equalsIgnoreCase(item.getLang()))
                .findFirst()
                .orElse(null);

        ProductResponse english = new ProductResponse(
                product.getId(), product.getName(), product.getProductName(), product.getStrength(),
                product.getPackSize(), product.getForm(), product.getPrice(), product.getScientificName(),
                product.getScientificCategory(), product.getCategory().getId(), product.getCategory().getName(),
                product.getCompany(), product.getRoute(), product.getDescription(), product.getImageUrl()
        );
        ProductResponse arabic = translation == null
                ? english
                : new ProductResponse(
                        product.getId(), translation.getName(), translation.getProductName(), translation.getStrength(),
                        translation.getPackSize(), translation.getForm(), product.getPrice(),
                        translation.getScientificName(), translation.getScientificCategory(),
                        product.getCategory().getId(), translation.getConsumerCategory(), translation.getCompany(),
                        translation.getRoute(), translation.getDescription(), product.getImageUrl()
                );

        String embeddingText = """
                English: %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s
                Arabic: %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s
                """.formatted(
                value(english.name()), value(english.productName()), value(english.strength()),
                value(english.packSize()), value(english.form()), value(english.scientificName()),
                value(english.scientificCategory()), value(english.consumerCategory()), value(english.company()),
                value(english.route()), value(english.description()), value(arabic.name()), value(arabic.productName()),
                value(arabic.strength()), value(arabic.packSize()), value(arabic.form()),
                value(arabic.scientificName()), value(arabic.scientificCategory()),
                value(arabic.consumerCategory()), value(arabic.company()), value(arabic.route()),
                value(arabic.description())
        ).trim();

        return new CatalogDocument(
                product.getId(), english, arabic, embeddingText, sha256(embeddingText),
                normalizeText(english.name()), normalizeText(arabic.name()),
                normalizeText(english.productName()), normalizeText(arabic.productName()),
                normalizeText(english.scientificName() + " " + arabic.scientificName()),
                normalizeText(embeddingText)
        );
    }

    private ProductMatchResponse score(
            CatalogDocument document,
            String query,
            float[] queryVector,
            Snapshot current,
            String language
    ) {
        double lexical = lexicalScore(query, document);
        double semantic = 0;
        float[] productVector = current.vectors().get(document.productId());
        if (queryVector != null && productVector != null && queryVector.length == productVector.length) {
            semantic = Math.max(0, cosineSimilarity(queryVector, productVector));
        }

        double weight = properties.getRetrieval().getSemanticWeight();
        double combined = queryVector == null
                ? lexical
                : semantic * weight + lexical * (1 - weight);
        if (lexical >= 0.95) {
            combined = Math.max(combined, 0.95 + semantic * 0.05);
        } else if (lexical >= 0.80) {
            combined = Math.max(combined, 0.82 + semantic * 0.08);
        }

        return new ProductMatchResponse(
                document.localized(language),
                round(combined),
                round(semantic),
                round(lexical),
                matchReason(lexical, semantic, queryVector != null)
        );
    }

    private double lexicalScore(String query, CatalogDocument document) {
        double nameScore = Math.max(
                Math.max(nameScore(query, document.normalizedEnglishName()),
                        nameScore(query, document.normalizedArabicName())),
                Math.max(nameScore(query, document.normalizedEnglishProductName()),
                        nameScore(query, document.normalizedArabicProductName()))
        );
        double scientificScore = document.normalizedScientificName().contains(query) ? 0.82 : 0;
        double fieldScore = document.normalizedSearchText().contains(query) ? 0.72 : 0;
        double tokenScore = tokenCoverage(query, document.normalizedSearchText()) * 0.68;
        return Math.max(Math.max(nameScore, scientificScore), Math.max(fieldScore, tokenScore));
    }

    private double nameScore(String query, String name) {
        if (name.isBlank()) {
            return 0;
        }
        if (name.equals(query)) {
            return 1;
        }
        if (name.contains(query)) {
            return 0.92;
        }
        if (query.contains(name)) {
            return 0.86;
        }
        double similarity = normalizedLevenshtein(query, name);
        return similarity >= 0.70 ? similarity * 0.82 : 0;
    }

    private double tokenCoverage(String query, String text) {
        Set<String> queryTokens = new HashSet<>(Arrays.asList(query.split(" ")));
        queryTokens.removeIf(String::isBlank);
        if (queryTokens.isEmpty()) {
            return 0;
        }
        Set<String> textTokens = new HashSet<>(Arrays.asList(text.split(" ")));
        return (double) queryTokens.stream().filter(textTokens::contains).count() / queryTokens.size();
    }

    private double normalizedLevenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }
            int[] temporary = previous;
            previous = current;
            current = temporary;
        }
        int longest = Math.max(left.length(), right.length());
        return longest == 0 ? 1 : 1 - ((double) previous[right.length()] / longest);
    }

    private double cosineSimilarity(float[] left, float[] right) {
        double dot = 0;
        double leftMagnitude = 0;
        double rightMagnitude = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftMagnitude += left[index] * left[index];
            rightMagnitude += right[index] * right[index];
        }
        if (leftMagnitude == 0 || rightMagnitude == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private String matchReason(double lexical, double semantic, boolean semanticUsed) {
        if (lexical >= 0.95) {
            return "exact-name";
        }
        if (lexical >= 0.80) {
            return "name-or-ingredient";
        }
        if (semanticUsed && semantic >= 0.55 && lexical > 0) {
            return "hybrid";
        }
        return semanticUsed ? "semantic" : "lexical";
    }

    private String buildContext(List<ProductMatchResponse> matches) {
        StringBuilder context = new StringBuilder();
        for (ProductMatchResponse match : matches) {
            ProductResponse product = match.product();
            context.append("PRODUCT\n")
                    .append("id: ").append(product.id()).append('\n')
                    .append("name: ").append(value(product.name())).append('\n')
                    .append("productName: ").append(value(product.productName())).append('\n')
                    .append("strength: ").append(value(product.strength())).append('\n')
                    .append("packSize: ").append(value(product.packSize())).append('\n')
                    .append("form: ").append(value(product.form())).append('\n')
                    .append("scientificName: ").append(value(product.scientificName())).append('\n')
                    .append("scientificCategory: ").append(value(product.scientificCategory())).append('\n')
                    .append("consumerCategory: ").append(value(product.consumerCategory())).append('\n')
                    .append("company: ").append(value(product.company())).append('\n')
                    .append("route: ").append(value(product.route())).append('\n')
                    .append("description: ").append(value(product.description())).append('\n')
                    .append("listedPriceEGP: ").append(product.price()).append("\n\n");
        }
        return context.toString();
    }

    private int normalizeLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? properties.getRetrieval().getDefaultTopK() : requestedLimit;
        if (limit < 1 || limit > properties.getRetrieval().getMaxTopK()) {
            throw new IllegalArgumentException(
                    "Result limit must be between 1 and " + properties.getRetrieval().getMaxTopK()
            );
        }
        return limit;
    }

    private String normalizeLanguage(String language) {
        String normalized = language == null || language.isBlank()
                ? "en"
                : language.trim().toLowerCase(Locale.ROOT);
        if (!"en".equals(normalized) && !"ar".equals(normalized)) {
            throw new IllegalArgumentException("Language must be en or ar");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replace('\u0623', '\u0627')
                .replace('\u0625', '\u0627')
                .replace('\u0622', '\u0627')
                .replace('\u0649', '\u064a')
                .replace('\u0624', '\u0648')
                .replace('\u0626', '\u064a')
                .replace('\u0629', '\u0647')
                .replace("\u0640", "")
                .replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float component : vector) {
            buffer.putFloat(component);
        }
        return buffer.array();
    }

    private float[] decode(byte[] bytes, int dimensions) {
        if (bytes == null || dimensions <= 0 || bytes.length != dimensions * Float.BYTES) {
            throw new IllegalArgumentException("Stored embedding has invalid dimensions");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[dimensions];
        for (int index = 0; index < dimensions; index++) {
            vector[index] = buffer.getFloat();
        }
        return vector;
    }

    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    private record CatalogDocument(
            Long productId,
            ProductResponse english,
            ProductResponse arabic,
            String embeddingText,
            String contentHash,
            String normalizedEnglishName,
            String normalizedArabicName,
            String normalizedEnglishProductName,
            String normalizedArabicProductName,
            String normalizedScientificName,
            String normalizedSearchText
    ) {
        ProductResponse localized(String language) {
            return "ar".equals(language) ? arabic : english;
        }
    }

    private record Snapshot(List<CatalogDocument> documents, Map<Long, float[]> vectors) {
        static Snapshot empty() {
            return new Snapshot(List.of(), Map.of());
        }
    }
}
