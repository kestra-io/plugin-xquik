package io.kestra.plugin.xquik.users;

import com.x_twitter_scraper.api.models.x.users.UserRetrieveTweetsParams;
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
    title = "List user tweets",
    description = "Retrieves recent public posts from an X/Twitter user through Xquik."
)
@Plugin(
    examples = {
        @Example(
            title = "Store a user's recent posts.",
            full = true,
            code = """
                id: xquik_user_tweets
                namespace: company.research

                tasks:
                  - id: user_posts
                    type: io.kestra.plugin.xquik.users.Tweets
                    apiKey: "{{ secret('XQUIK_API_KEY') }}"
                    user: "kestra_io"
                    includeReplies: false
                    fetchType: STORE
                """
        )
    }
)
public class Tweets extends AbstractXquikTask {
    @Schema(title = "User", description = "X username without `@`, or a numeric X user ID.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> user;

    @Schema(title = "Pagination cursor", description = "Cursor from a previous response.")
    @PluginProperty(group = "advanced")
    private Property<String> cursor;

    @Schema(title = "Include replies", description = "Whether to include reply posts.")
    @lombok.Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> includeReplies = Property.ofValue(false);

    @Schema(title = "Include parent tweet", description = "Whether to include parent tweet data for replies.")
    @lombok.Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> includeParentTweet = Property.ofValue(false);

    @Schema(title = "Additional query parameters", description = "Optional Xquik tweet filters, such as language, hashtags, mediaType, or sinceDate.")
    @PluginProperty(group = "advanced")
    private Property<Map<String, Object>> additionalQueryParameters;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Map<String, Object> additional = additionalQueryParameters(runContext, this.additionalQueryParameters);

        UserRetrieveTweetsParams.Builder params = UserRetrieveTweetsParams.builder()
            .id(runContext.render(this.user).as(String.class).orElseThrow().replaceFirst("^@", ""));

        // A key given in additionalQueryParameters replaced the property before the SDK migration.
        if (!additional.containsKey("cursor")) {
            runContext.render(this.cursor).as(String.class).filter(value -> !value.isBlank()).ifPresent(params::cursor);
        }

        if (!additional.containsKey("includeReplies")) {
            runContext.render(this.includeReplies).as(Boolean.class).ifPresent(params::includeReplies);
        }

        if (!additional.containsKey("includeParentTweet")) {
            runContext.render(this.includeParentTweet).as(Boolean.class).ifPresent(params::includeParentTweet);
        }

        additional.forEach((key, value) -> params.putAdditionalQueryParam(key, String.valueOf(value)));

        return call(runContext, client -> client.x().users().withRawResponse().retrieveTweets(params.build()));
    }
}
