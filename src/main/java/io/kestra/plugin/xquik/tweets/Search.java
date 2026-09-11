package io.kestra.plugin.xquik.tweets;

import com.x_twitter_scraper.api.models.x.tweets.TweetSearchParams;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.xquik.AbstractXquikTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search tweets",
    description = "Searches public X/Twitter posts with Xquik and returns a structured response for downstream Kestra tasks."
)
@Plugin(
    examples = {
        @Example(
            title = "Search recent posts about an incident.",
            full = true,
            code = """
                id: xquik_tweet_search
                namespace: company.research

                tasks:
                  - id: search_posts
                    type: io.kestra.plugin.xquik.tweets.Search
                    apiKey: "{{ secret('XQUIK_API_KEY') }}"
                    query: "from:kestra_io orchestration"
                    queryType: Latest
                    limit: 20
                """
        ),
        @Example(
            title = "Store a larger search response.",
            full = true,
            code = """
                id: xquik_tweet_search_store
                namespace: company.research

                tasks:
                  - id: search_posts
                    type: io.kestra.plugin.xquik.tweets.Search
                    apiKey: "{{ secret('XQUIK_API_KEY') }}"
                    query: "agent framework"
                    limit: 100
                    fetchType: STORE
                """
        )
    }
)
public class Search extends AbstractXquikTask {
    public enum QueryType {
        Latest,
        Top
    }

    @Schema(title = "Search query", description = "X search query, including standard operators such as `from:`, `to:`, or hashtags.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> query;

    @Schema(title = "Query type", description = "Sort order. `Latest` returns chronological results, while `Top` returns engagement-ranked results.")
    @lombok.Builder.Default
    @PluginProperty(group = "main")
    private Property<QueryType> queryType = Property.ofValue(QueryType.Latest);

    @Schema(title = "Result limit", description = "Maximum tweets to request from Xquik.")
    @lombok.Builder.Default
    @PluginProperty(group = "main")
    private Property<Integer> limit = Property.ofValue(20);

    @Schema(title = "Pagination cursor", description = "Cursor from a previous response.")
    @PluginProperty(group = "advanced")
    private Property<String> cursor;

    @Schema(title = "Since time", description = "ISO 8601 timestamp. Only returns tweets after this time.")
    @PluginProperty(group = "advanced")
    private Property<String> sinceTime;

    @Schema(title = "Until time", description = "ISO 8601 timestamp. Only returns tweets before this time.")
    @PluginProperty(group = "advanced")
    private Property<String> untilTime;

    @Schema(title = "Additional query parameters", description = "Optional Xquik tweet-search filters, such as language, hashtags, mediaType, or minFaves.")
    @PluginProperty(group = "advanced")
    private Property<Map<String, Object>> additionalQueryParameters;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Map<String, Object> additional = additionalQueryParameters(runContext, this.additionalQueryParameters);

        TweetSearchParams.Builder params = TweetSearchParams.builder()
            .q(additional.containsKey("q")
                ? String.valueOf(additional.get("q"))
                : runContext.render(this.query).as(String.class).orElseThrow());

        renderedValue(runContext, this.queryType, QueryType.class, additional, "queryType")
            .ifPresent(value -> params.queryType(TweetSearchParams.QueryType.of(value.name())));
        renderedValue(runContext, this.limit, Integer.class, additional, "limit")
            .ifPresent(value -> params.limit(value.longValue()));
        renderedValue(runContext, this.cursor, String.class, additional, "cursor").ifPresent(params::cursor);
        renderedValue(runContext, this.sinceTime, String.class, additional, "sinceTime").ifPresent(params::sinceTime);
        renderedValue(runContext, this.untilTime, String.class, additional, "untilTime").ifPresent(params::untilTime);

        for (Map.Entry<String, Object> entry : additional.entrySet()) {
            if (!"q".equals(entry.getKey())) {
                params.putAdditionalQueryParam(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        return call(runContext, client -> client.x().tweets().withRawResponse().search(params.build()));
    }
}
