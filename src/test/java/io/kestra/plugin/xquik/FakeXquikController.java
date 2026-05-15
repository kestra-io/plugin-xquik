package io.kestra.plugin.xquik;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;

import java.util.HashMap;
import java.util.Map;

@Controller("/api/v1/x")
@Produces(MediaType.APPLICATION_JSON)
public class FakeXquikController {
    public static Map<String, String> headers = new HashMap<>();
    public static Map<String, String> queryParameters = new HashMap<>();
    public static String lastPath;

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
        lastPath = path;
        headers = new HashMap<>();
        request.getHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));

        queryParameters = new HashMap<>();
        request.getParameters().forEach((name, values) -> queryParameters.put(name, values.getFirst()));
    }
}
