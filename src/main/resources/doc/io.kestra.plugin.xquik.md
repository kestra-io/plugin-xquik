# How to use the Xquik plugin

Retrieve public X/Twitter posts, users, and trends via the Xquik API from Kestra flows.

## Authentication

All tasks require `apiKey` (your Xquik API key, required). Optionally set `baseUrl` (default `https://xquik.com/api/v1`) and `options` for HTTP client configuration (connect timeout, read idle timeout, custom headers). Store the API key in [secrets](https://kestra.io/docs/concepts/secret) and apply connection properties globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

All tasks share a `fetchType` property (default `FETCH`): `FETCH` returns results inline as `body`; `FETCH_ONE` returns the first result; `STORE` writes results to Kestra internal storage and outputs a `uri`. Paginated tasks also output `nextCursor` and `hasNextPage`.

`tweets.Search` searches public posts — set `query` (required, supports X search operators). Optionally set `queryType` (`Latest` or `Top`, default `Latest`), `limit` (default `20`), `cursor` (pagination), `sinceTime` and `untilTime` (ISO 8601 timestamps), and `additionalQueryParameters` for Xquik-specific filters.

`tweets.Get` fetches a single post by ID — set `tweetId` (required).

`users.Search` searches public user accounts — set `query` (required). Optionally set `cursor` for pagination.

`users.Get` fetches a single user profile — set `user` (required; accepts either a username or a numeric user ID).

`users.Tweets` fetches recent posts from a user — set `user` (required). Optionally set `includeReplies` (default `false`), `includeParentTweet` (default `false`), `cursor`, and `additionalQueryParameters`.

`trends.List` returns trending topics for a region — set `woeid` (Yahoo Where On Earth ID, default `1` for worldwide) and `count` (default `30`).
