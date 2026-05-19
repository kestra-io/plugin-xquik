package io.kestra.plugin.xquik;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Controller("/api/v1/x")
@Produces(MediaType.APPLICATION_JSON)
public class FakeXquikController {
    private static final Map<String, String> headers = new ConcurrentHashMap<>();
    private static final Map<String, String> queryParameters = new ConcurrentHashMap<>();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();

    public static Map<String, String> headers() {
        return headers;
    }

    public static Map<String, String> queryParameters() {
        return queryParameters;
    }

    public static String lastPath() {
        return lastPath.get();
    }

    public static void reset() {
        headers.clear();
        queryParameters.clear();
        lastPath.set(null);
    }

    @Get("/tweets/search")
    public HttpResponse<String> searchTweets(HttpRequest<?> request) {
        capture(request, "/x/tweets/search");
        return HttpResponse.ok("""
            {
              "tweets": [
                {
                  "id": "1900000000000000001",
                  "text": "Kestra can orchestrate Xquik research tasks."
                }
              ],
              "has_next_page": true,
              "next_cursor": "next-page"
            }
            """);
    }

    @Get("/tweets/{id}")
    public HttpResponse<String> getTweet(HttpRequest<?> request, @PathVariable String id) {
        capture(request, "/x/tweets/" + id);
        return HttpResponse.ok("""
            {
              "id": "%s",
              "text": "A public post"
            }
            """.formatted(id));
    }

    @Get("/users/search")
    public HttpResponse<String> searchUsers(HttpRequest<?> request) {
        capture(request, "/x/users/search");
        return HttpResponse.ok("""
            {
              "users": [
                {
                  "id": "1000000001",
                  "username": "kestra_io"
                }
              ],
              "has_next_page": false
            }
            """);
    }

    @Get("/users/{id}")
    public HttpResponse<String> getUser(HttpRequest<?> request, @PathVariable String id) {
        capture(request, "/x/users/" + id);
        return HttpResponse.ok("""
            {
              "id": "1000000001",
              "username": "%s"
            }
            """.formatted(id));
    }

    @Get("/users/{id}/tweets")
    public HttpResponse<String> getUserTweets(HttpRequest<?> request, @PathVariable String id) {
        capture(request, "/x/users/" + id + "/tweets");
        return HttpResponse.ok("""
            {
              "tweets": [
                {
                  "id": "1900000000000000002",
                  "text": "User timeline post"
                }
              ],
              "has_next_page": false
            }
            """);
    }

    @Get("/trends")
    public HttpResponse<String> listTrends(HttpRequest<?> request) {
        capture(request, "/x/trends");
        return HttpResponse.ok("""
            {
              "trends": [
                {
                  "name": "#AIAgents",
                  "rank": 1
                }
              ]
            }
            """);
    }

    private void capture(HttpRequest<?> request, String path) {
        lastPath.set(path);
        headers.clear();
        request.getHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));

        queryParameters.clear();
        request.getParameters().forEach((name, values) -> queryParameters.put(name, values.getFirst()));
    }
}
