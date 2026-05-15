package io.kestra.plugin.xquik.tweets;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.xquik.AbstractXquikTask;
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Get tweet",
    description = "Retrieves a public X/Twitter post by ID through Xquik."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch a tweet by ID.",
            full = true,
            code = """
                id: xquik_get_tweet
                namespace: company.research

                tasks:
                  - id: get_tweet
                    type: io.kestra.plugin.xquik.tweets.Get
                    apiKey: "{{ secret('XQUIK_API_KEY') }}"
                    tweetId: "1900000000000000001"
                """
        )
    }
)
public class Get extends AbstractXquikTask {
    @Schema(title = "Tweet ID", description = "X/Twitter post ID.")
    @PluginProperty(group = "main")
    private Property<String> tweetId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedTweetId = runContext.render(this.tweetId).as(String.class).orElseThrow();
        return get(runContext, "/x/tweets/" + pathSegment(renderedTweetId), Map.of());
    }
}
