package io.kestra.plugin.xquik;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString(exclude = "apiKey")
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractXquikTask extends Task implements RunnableTask<AbstractXquikTask.Output> {
    private static final String DEFAULT_BASE_URL = "https://xquik.com/api/v1";

    @Schema(
        title = "Xquik API key",
        description = "API key used to authenticate Xquik API requests."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    protected Property<String> apiKey;

    @Schema(
        title = "Xquik API base URL",
        description = "Base URL for Xquik API requests."
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<String> baseUrl = Property.ofValue(DEFAULT_BASE_URL);

    @Schema(
        title = "Result handling mode",
        description = "Controls how the response is exposed. `FETCH` returns the response body, `STORE` writes it to Kestra internal storage, and `NONE` omits the body."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Schema(
        title = "HTTP request options",
        description = "Options used to customize the HTTP client."
    )
    @PluginProperty(group = "advanced")
    protected RequestOptions options;

    protected Output get(RunContext runContext, String path, Map<String, Object> queryParameters) throws Exception {
        String renderedBaseUrl = runContext.render(this.baseUrl).as(String.class).orElse(DEFAULT_BASE_URL);
        String renderedApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();
        FetchType renderedFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

        URI uri = URI.create(trimTrailingSlash(renderedBaseUrl) + path + queryString(runContext, queryParameters));
        HttpRequest request = createRequestBuilder(runContext)
            .uri(uri)
            .method("GET")
            .addHeader("Accept", "application/json")
            .addHeader("x-api-key", renderedApiKey)
            .build();

        try (HttpClient client = new HttpClient(runContext, httpClientConfigurationWithOptions(runContext))) {
            HttpResponse<String> response = client.request(request, String.class);
            int statusCode = response.getStatus().getCode();

            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException(
                    "Xquik request failed with HTTP status code " + statusCode + responseBodySuffix(response.getBody())
                );
            }

            Map<String, Object> body = JacksonMapper.ofJson().readValue(
                response.getBody(),
                new TypeReference<>() {}
            );

            return handleFetch(runContext, body, renderedFetchType);
        }
    }

    protected Map<String, Object> parameters(Map<String, Object> values) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null && !String.valueOf(value).isBlank()) {
                filtered.put(key, value);
            }
        });
        return filtered;
    }

    protected String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    protected Optional<Map<String, Object>> renderedMap(RunContext runContext, Property<Map<String, Object>> value)
        throws IllegalVariableEvaluationException {
        return Optional.ofNullable(runContext.render(value).asMap(String.class, Object.class));
    }

    private Output handleFetch(RunContext runContext, Map<String, Object> body, FetchType renderedFetchType) throws Exception {
        List<?> rows = responseRows(body);
        Integer size = rows.size();
        String nextCursor = stringValue(body.get("next_cursor")).orElseGet(() -> stringValue(body.get("nextCursor")).orElse(null));
        Boolean hasNextPage = booleanValue(body.get("has_next_page")).orElseGet(() -> booleanValue(body.get("hasNextPage")).orElse(null));

        return switch (renderedFetchType) {
            case FETCH, FETCH_ONE -> Output.builder()
                .body(body)
                .size(size)
                .nextCursor(nextCursor)
                .hasNextPage(hasNextPage)
                .build();
            case STORE -> {
                java.io.File tempFile = runContext.workingDir().createTempFile(".ion").toFile();

                try (Writer output = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                    FileSerde.writeAll(output, Flux.fromIterable(rows)).block();
                }

                yield Output.builder()
                    .uri(runContext.storage().putFile(tempFile))
                    .size(size)
                    .nextCursor(nextCursor)
                    .hasNextPage(hasNextPage)
                    .build();
            }
            default -> Output.builder().size(0).build();
        };
    }

    private List<?> responseRows(Map<String, Object> body) {
        for (String key : List.of("tweets", "users", "trends", "items", "data")) {
            Object value = body.get(key);
            if (value instanceof List<?> list) {
                return list;
            }
        }

        Optional<List<?>> firstList = body.entrySet()
            .stream()
            .filter(entry -> entry.getValue() instanceof List<?>)
            .sorted(Map.Entry.comparingByKey())
            .<List<?>>map(entry -> (List<?>) entry.getValue())
            .findFirst();

        return firstList.orElseGet(() -> body.isEmpty() ? List.of() : List.of(body));
    }

    private Optional<String> stringValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(String.valueOf(value));
    }

    private Optional<Boolean> booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return Optional.of(booleanValue);
        }

        return Optional.empty();
    }

    private String responseBodySuffix(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        String excerpt = body.length() > 1000 ? body.substring(0, 1000) + "..." : body;
        return " with response body: " + excerpt;
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private String queryString(RunContext runContext, Map<String, Object> queryParameters) throws IllegalVariableEvaluationException {
        Map<String, Object> renderedParameters = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : queryParameters.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Property<?> property) {
                value = renderPropertyValue(runContext, property);
            }

            if (value != null && !String.valueOf(value).isBlank()) {
                renderedParameters.put(entry.getKey(), value);
            }
        }

        if (renderedParameters.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder("?");
        boolean first = true;

        for (Map.Entry<String, Object> entry : renderedParameters.entrySet()) {
            if (!first) {
                builder.append("&");
            }

            builder
                .append(encode(entry.getKey()))
                .append("=")
                .append(encode(String.valueOf(entry.getValue())));
            first = false;
        }

        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object renderPropertyValue(RunContext runContext, Property<?> property) throws IllegalVariableEvaluationException {
        // Attempt numeric/boolean rendering first; fall back to String for everything else.
        var rendered = runContext.render((Property) property);

        var asInteger = rendered.as(Integer.class);
        if (asInteger.isPresent()) {
            return asInteger.get();
        }

        var asLong = rendered.as(Long.class);
        if (asLong.isPresent()) {
            return asLong.get();
        }

        var asDouble = rendered.as(Double.class);
        if (asDouble.isPresent()) {
            return asDouble.get();
        }

        var asBoolean = rendered.as(Boolean.class);
        if (asBoolean.isPresent()) {
            return asBoolean.get();
        }

        return rendered.as(String.class).orElse(null);
    }

    private HttpConfiguration httpClientConfigurationWithOptions(RunContext runContext) throws IllegalVariableEvaluationException {
        HttpConfiguration.HttpConfigurationBuilder configuration = HttpConfiguration.builder();

        if (this.options != null) {
            configuration
                .timeout(
                    TimeoutConfiguration.builder()
                        .connectTimeout(renderedProperty(runContext, this.options.getConnectTimeout(), Duration.class))
                        .readIdleTimeout(renderedProperty(runContext, this.options.getReadIdleTimeout(), Duration.class))
                        .build()
                )
                .defaultCharset(renderedProperty(runContext, this.options.getDefaultCharset(), Charset.class));
        }

        return configuration.build();
    }

    private <T> Property<T> renderedProperty(RunContext runContext, Property<T> property, Class<T> type)
        throws IllegalVariableEvaluationException {
        if (property == null) {
            return null;
        }

        return runContext.render(property).as(type).map(Property::ofValue).orElse(null);
    }

    private HttpRequest.HttpRequestBuilder createRequestBuilder(RunContext runContext) throws IllegalVariableEvaluationException {
        HttpRequest.HttpRequestBuilder builder = HttpRequest.builder();

        if (this.options != null && this.options.getHeaders() != null) {
            Map<String, String> headers = runContext.render(this.options.getHeaders())
                .asMap(String.class, String.class);

            if (headers != null) {
                headers.forEach(builder::addHeader);
            }
        }

        return builder;
    }

    @Getter
    @Builder
    public static class RequestOptions {
        @Schema(title = "Connection timeout", description = "Time allowed to establish a server connection before failing.")
        @PluginProperty(group = "execution")
        private final Property<Duration> connectTimeout;

        @Schema(title = "Read idle timeout", description = "How long a read connection may stay idle before closing. Defaults to 5 minutes.")
        @Builder.Default
        @PluginProperty(group = "execution")
        private final Property<Duration> readIdleTimeout = Property.ofValue(Duration.of(5, ChronoUnit.MINUTES));

        @Schema(title = "Default charset", description = "Charset used for requests when none is specified. Defaults to UTF-8.")
        @Builder.Default
        @PluginProperty(group = "advanced")
        private final Property<Charset> defaultCharset = Property.ofValue(StandardCharsets.UTF_8);

        @Schema(title = "HTTP headers", description = "HTTP headers to include in the request.")
        @PluginProperty(group = "advanced")
        public Property<Map<String, String>> headers;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Response item count", description = "Best-effort count inferred from the first array in the response body.")
        private Integer size;

        @Schema(title = "Response body", description = "Response payload. Available when `fetchType` is `FETCH` or `FETCH_ONE`.")
        private Map<String, Object> body;

        @Schema(title = "Stored response URI", description = "Kestra internal storage URI. Available when `fetchType` is `STORE`.")
        private URI uri;

        @Schema(title = "Next cursor", description = "Pagination cursor returned by Xquik when present.")
        private String nextCursor;

        @Schema(title = "Has next page", description = "Whether Xquik reported another page when present.")
        private Boolean hasNextPage;
    }
}
