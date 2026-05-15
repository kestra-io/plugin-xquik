package io.kestra.plugin.xquik.users;

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
    @PluginProperty(group = "main")
    private Property<String> user;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedUser = runContext.render(this.user).as(String.class).orElseThrow().replaceFirst("^@", "");
        return get(runContext, "/x/users/" + pathSegment(renderedUser), Map.of());
    }
}
