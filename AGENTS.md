# Kestra Xquik Plugin

## What

- Provides plugin components under `io.kestra.plugin.xquik`.
- Covers read-only Xquik API endpoints: tweet search, user lookup, user timeline, and trend data.

## Why

- What user problem does this solve? Teams need a reliable, scheduled way to collect public X/Twitter data without building custom API integrations from scratch.
- Why would a team adopt this plugin in a workflow? It provides ready-made tasks for the Xquik X/Twitter API, enabling direct integration with downstream notifications, data warehouse updates, or trend-monitoring workflows.
- What operational/business outcome does it enable? It reduces integration effort, centralises credential management, and makes X/Twitter data collection repeatable and observable within Kestra.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `xquik`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.xquik` (package — classes to be added per endpoint)

### Project Structure

```
plugin-xquik/
├── src/main/java/io/kestra/plugin/xquik/
├── src/test/java/io/kestra/plugin/xquik/
├── build.gradle
└── README.md
```

## Local rules

- Base the wording on the implemented packages and classes, not on template README text.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
