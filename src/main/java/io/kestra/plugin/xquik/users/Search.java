package io.kestra.plugin.xquik.users;

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

import java.util.LinkedHashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Search users",
    description = "Searches public X/Twitter users through Xquik."
)
@Plugin(
    examples = {
        @Example(
            title = "Search X users.",
            full = true,
            code = """
                id: xquik_user_search
                namespace: company.research

                tasks:
                  - id: search_users
                    type: io.kestra.plugin.xquik.users.Search
                    apiKey: "{{ secret('XQUIK_API_KEY') }}"
                    query: "Kestra"
                """
        )
    }
)
public class Search extends AbstractXquikTask {
    @Schema(title = "Search query", description = "User search query.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> query;

    @Schema(title = "Pagination cursor", description = "Cursor from a previous response.")
    @PluginProperty(group = "advanced")
    private Property<String> cursor;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", this.query);
        params.put("cursor", this.cursor);

        return get(runContext, "/x/users/search", params);
    }
}
