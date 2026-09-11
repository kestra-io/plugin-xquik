package io.kestra.plugin.xquik.trends;

import com.x_twitter_scraper.api.models.x.XGetTrendsParams;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List trends",
    description = "Retrieves public X/Twitter trending topics for a region through Xquik."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch worldwide trends.",
            full = true,
            code = """
                id: xquik_trends
                namespace: company.research

                tasks:
                  - id: list_trends
                    type: io.kestra.plugin.xquik.trends.List
                    apiKey: "{{ secret('XQUIK_API_KEY') }}"
                    woeid: 1
                    count: 10
                """
        )
    }
)
public class List extends AbstractXquikTask {
    @Schema(title = "WOEID", description = "Region WOEID. Use `1` for worldwide trends.")
    @lombok.Builder.Default
    @PluginProperty(group = "main")
    private Property<Integer> woeid = Property.ofValue(1);

    @Schema(title = "Trend count", description = "Number of trending topics to return.")
    @lombok.Builder.Default
    @PluginProperty(group = "main")
    private Property<Integer> count = Property.ofValue(30);

    @Override
    public Output run(RunContext runContext) throws Exception {
        XGetTrendsParams.Builder params = XGetTrendsParams.builder();

        runContext.render(this.woeid).as(Integer.class).ifPresent(value -> params.woeid(value.longValue()));
        runContext.render(this.count).as(Integer.class).ifPresent(value -> params.count(value.longValue()));

        return call(runContext, client -> client.x().withRawResponse().getTrends(params.build()));
    }
}
