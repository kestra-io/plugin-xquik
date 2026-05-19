<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<div align="center">
 <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

<p align="center">
  <a href="https://twitter.com/kestra_io" style="margin: 0 10px;">
        <img src="https://kestra.io/twitter.svg" alt="twitter" width="35" height="25" /></a>
  <a href="https://www.linkedin.com/company/kestra/" style="margin: 0 10px;">
        <img src="https://kestra.io/linkedin.svg" alt="linkedin" width="35" height="25" /></a>
  <a href="https://www.youtube.com/@kestra-io" style="margin: 0 10px;">
        <img src="https://kestra.io/youtube.svg" alt="youtube" width="35" height="25" /></a>
</p>

<br />
<p align="center">
    <a href="https://go.kestra.io/video/product-overview" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 3 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>Get started with Kestra in 3 minutes.</i></p>

# Kestra Xquik Plugin

Run read-only Xquik API calls from Kestra flows. This plugin retrieves public
X/Twitter posts, users, timelines, and trends for scheduled research,
monitoring, reporting, and downstream automation.

## Why

- Teams need a reliable, scheduled way to collect public X/Twitter data without
  building custom API integrations.
- Xquik tasks return structured responses that can feed notifications, storage,
  analytics, or approval workflows.
- Kestra manages scheduling, secrets, retries, observability, and downstream
  orchestration around the API call.

## What

- Provides plugin components under `io.kestra.plugin.xquik`.
- Covers read-only Xquik API endpoints: tweet search, tweet lookup, user search,
  user lookup, user timeline, and trend data.
- Supports `fetchType` so larger search or timeline responses can be stored in
  Kestra internal storage.

## Tasks

- `io.kestra.plugin.xquik.tweets.Search`
- `io.kestra.plugin.xquik.tweets.Get`
- `io.kestra.plugin.xquik.users.Search`
- `io.kestra.plugin.xquik.users.Get`
- `io.kestra.plugin.xquik.users.Tweets`
- `io.kestra.plugin.xquik.trends.List`

## Example

```yaml
id: xquik_daily_research
namespace: company.research

tasks:
  - id: search_posts
    type: io.kestra.plugin.xquik.tweets.Search
    apiKey: "{{ secret('XQUIK_API_KEY') }}"
    query: "from:kestra_io orchestration"
    queryType: Latest
    limit: 20

  - id: list_trends
    type: io.kestra.plugin.xquik.trends.List
    apiKey: "{{ secret('XQUIK_API_KEY') }}"
    woeid: 1
    count: 10
```

## Documentation
* Full documentation can be found under: [kestra.io/docs](https://kestra.io/docs)
* Documentation for developing a plugin is included in the [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/)


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)


## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)
