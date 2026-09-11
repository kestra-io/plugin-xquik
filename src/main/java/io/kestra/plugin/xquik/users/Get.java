package io.kestra.plugin.xquik.users;

import com.x_twitter_scraper.api.models.x.users.UserRetrieveParams;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get user",
    description = "Retrieves a public X/Twitter user profile by username or user ID through Xquik."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch an X user profile.",
            full = true,
            code = """
                id: xquik_get_user
                namespace: company.research

                tasks:
                  - id: get_user
                    type: io.kestra.plugin.xquik.users.Get
                    apiKey: "{{ secret('XQUIK_API_KEY') }}"
                    user: "kestra_io"
                """
        )
    }
)
public class Get extends AbstractXquikTask {
    @Schema(title = "User", description = "X username without `@`, or a numeric X user ID.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> user;

    @Override
    public Output run(RunContext runContext) throws Exception {
        UserRetrieveParams params = UserRetrieveParams.builder()
            .id(runContext.render(this.user).as(String.class).orElseThrow().replaceFirst("^@", ""))
            .build();

        return call(runContext, client -> client.x().users().withRawResponse().retrieve(params));
    }
}
