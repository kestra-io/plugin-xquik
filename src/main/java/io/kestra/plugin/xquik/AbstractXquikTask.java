package io.kestra.plugin.xquik;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.x_twitter_scraper.api.client.XTwitterScraperClient;
import com.x_twitter_scraper.api.client.okhttp.XTwitterScraperOkHttpClient;
import com.x_twitter_scraper.api.core.ObjectMappers;
import com.x_twitter_scraper.api.core.Timeout;
import com.x_twitter_scraper.api.core.http.HttpResponseFor;
import com.x_twitter_scraper.api.errors.XTwitterScraperServiceException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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

    /** One Xquik SDK call, kept as a lambda so each task only builds its own typed params. */
    @FunctionalInterface
    protected interface XquikCall {
        HttpResponseFor<?> execute(XTwitterScraperClient client);
    }

    protected Output call(RunContext runContext, XquikCall call) throws Exception {
        FetchType renderedFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

        XTwitterScraperClient client = client(runContext);

        try {
            // Read the untouched response stream rather than the SDK's typed model, so the `body`
            // output stays exactly the JSON Xquik returned.
            try (HttpResponseFor<?> response = call.execute(client)) {
                Map<String, Object> body = JacksonMapper.ofJson().readValue(
                    response.body(),
                    new TypeReference<>() {}
                );

                return handleFetch(runContext, body, renderedFetchType);
            }
        } catch (XTwitterScraperServiceException e) {
            throw new IllegalStateException(
                "Xquik request failed with HTTP status code " + e.statusCode() + responseBodySuffix(errorBody(e)),
                e
            );
        } finally {
            client.close();
        }
    }

    private XTwitterScraperClient client(RunContext runContext) throws IllegalVariableEvaluationException {
        XTwitterScraperOkHttpClient.Builder builder = XTwitterScraperOkHttpClient.builder()
            .apiKey(runContext.render(this.apiKey).as(String.class).orElseThrow())
            .baseUrl(runContext.render(this.baseUrl).as(String.class).orElse(DEFAULT_BASE_URL));

        if (this.options != null) {
            Timeout.Builder timeout = Timeout.builder()
                // Kestra's client caps idle reads but never the overall call, so leave the call uncapped.
                .request(Duration.ZERO);

            runContext.render(this.options.getConnectTimeout()).as(Duration.class).ifPresent(timeout::connect);
            runContext.render(this.options.getReadIdleTimeout()).as(Duration.class).ifPresent(timeout::read);
            builder.timeout(timeout.build());

            Map<String, String> headers = runContext.render(this.options.getHeaders()).asMap(String.class, String.class);
            if (headers != null) {
                headers.forEach(builder::putHeader);
            }

            Charset charset = runContext.render(this.options.getDefaultCharset()).as(Charset.class).orElse(StandardCharsets.UTF_8);
            if (!StandardCharsets.UTF_8.equals(charset)) {
                runContext.logger().warn(
                    "defaultCharset is set to {} but Xquik responses are always decoded as UTF-8; the value is ignored.",
                    charset
                );
            }
        }

        return builder.build();
    }

    /** Optional parameters are only sent when set and non-blank, matching the previous query string builder. */
    protected <T> Optional<T> renderedValue(RunContext runContext, Property<T> property, Class<T> type)
        throws IllegalVariableEvaluationException {
        if (property == null) {
            return Optional.empty();
        }

        return runContext.render(property).as(type)
            .filter(value -> !(value instanceof String string) || !string.isBlank());
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

    /** The SDK hands back the error body as a JsonValue; render it as JSON rather than a Java map toString. */
    private String errorBody(XTwitterScraperServiceException e) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(e.body());
        } catch (JsonProcessingException ignored) {
            return String.valueOf(e.body());
        }
    }

    private String responseBodySuffix(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        String excerpt = body.length() > 1000 ? body.substring(0, 1000) + "..." : body;
        return " with response body: " + excerpt;
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

        @Schema(title = "Default charset", description = "Ignored. Xquik responses are always decoded as UTF-8. Kept so existing flows keep validating.")
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
