package io.kestra.plugin.xquik;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class XquikTaskTest extends AbstractXquikTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void searchTweetsFetchesBodyAndPagination() throws Exception {
        var runContext = runContextFactory.of();

        var task = io.kestra.plugin.xquik.tweets.Search.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api/v1"))
            .apiKey(Property.ofValue("test-api-key"))
            .query(Property.ofValue("agent framework"))
            .queryType(Property.ofValue(io.kestra.plugin.xquik.tweets.Search.QueryType.Top))
            .limit(Property.ofValue(5))
            .build();

        var output = task.run(runContext);

        assertThat(FakeXquikController.lastPath, is("/x/tweets/search"));
        assertThat(FakeXquikController.headers.get("x-api-key"), is("test-api-key"));
        assertThat(FakeXquikController.queryParameters.get("q"), is("agent framework"));
        assertThat(FakeXquikController.queryParameters.get("queryType"), is("Top"));
        assertThat(FakeXquikController.queryParameters.get("limit"), is("5"));
        assertThat(output.getBody(), notNullValue());
        assertThat(output.getSize(), is(1));
        assertThat(output.getHasNextPage(), is(true));
        assertThat(output.getNextCursor(), is("next-page"));
    }

    @Test
    void userTweetsCanStoreBody() throws Exception {
        var runContext = runContextFactory.of();

        var task = io.kestra.plugin.xquik.users.Tweets.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api/v1"))
            .apiKey(Property.ofValue("test-api-key"))
            .user(Property.ofValue("@kestra_io"))
            .includeReplies(Property.ofValue(false))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var output = task.run(runContext);

        assertThat(FakeXquikController.lastPath, is("/x/users/kestra_io/tweets"));
        assertThat(FakeXquikController.queryParameters.get("includeReplies"), is("false"));
        assertThat(output.getUri(), notNullValue());
        assertThat(output.getBody(), is((Map<String, Object>) null));

        var rows = new ArrayList<Map<String, Object>>();
        try (var input = new BufferedReader(new InputStreamReader(
            storageInterface.get(TenantService.MAIN_TENANT, null, output.getUri())
        ))) {
            FileSerde.reader(input, row -> rows.add((Map<String, Object>) row));
        }

        assertThat(rows.size(), is(1));
        assertThat(rows.getFirst().toString(), containsString("User timeline post"));
    }

    @Test
    void trendsRenderRegionAndCount() throws Exception {
        var runContext = runContextFactory.of();

        var task = io.kestra.plugin.xquik.trends.List.builder()
            .baseUrl(Property.ofValue(embeddedServer.getURI() + "/api/v1"))
            .apiKey(Property.ofValue("test-api-key"))
            .woeid(Property.ofValue(23424969))
            .count(Property.ofValue(3))
            .build();

        var output = task.run(runContext);

        assertThat(FakeXquikController.lastPath, is("/x/trends"));
        assertThat(FakeXquikController.queryParameters.get("woeid"), is("23424969"));
        assertThat(FakeXquikController.queryParameters.get("count"), is("3"));
        assertThat(output.getSize(), is(1));
    }
}
