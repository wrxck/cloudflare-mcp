# Cloudflare MCP Server

An [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) server that exposes the entire Cloudflare API as tools. Dynamically generates one MCP tool per API operation from the bundled [Cloudflare OpenAPI spec](https://github.com/cloudflare/api-schemas) — 2,655 operations across 1,686 endpoints covering every Cloudflare service.

## How it works

```
Bundled Cloudflare OpenAPI spec (8.4 MB, 1,686 paths)
  → parse & resolve $refs (swagger-parser)
  → filter operations (optional tag/path/method filters)
  → for each operation:
      → derive tool name from operationId
      → merge path/query params + request body into a single JSON Schema
      → map HTTP method to MCP tool annotations (readOnly, destructive, idempotent)
      → register as an MCP tool with a call handler that calls the Cloudflare API
  → start MCP server over stdio
```

## Features

- **Entire Cloudflare API** — 2,655 operations across 436 service categories (Zones, DNS, Workers, R2, Pages, WAF, Load Balancing, Tunnels, and more)
- **Dynamic tool generation** — tools are generated at startup from the bundled OpenAPI 3.0.3 spec
- **MCP tool annotations** — `readOnlyHint`, `destructiveHint`, `idempotentHint` set automatically based on HTTP method
- **Tag filtering** — `--include-tags` / `--exclude-tags` to expose only the API categories you need
- **Path filtering** — `--include-paths` / `--exclude-paths` with glob patterns
- **Method filtering** — `--include-methods` / `--exclude-methods` to restrict HTTP methods
- **Security hardening** — response sanitization with cryptographic boundary markers (prompt injection defense), response truncation, rate limiting
- **Bearer token auth** — reads `CLOUDFLARE_API_TOKEN` environment variable

## Prerequisites

- **Java 21** or later
- **Cloudflare API token** — [create one here](https://dash.cloudflare.com/profile/api-tokens)

## Quick start

### 1. Build

```bash
git clone https://github.com/wrxck/cloudflare-mcp.git
cd cloudflare-mcp
mvn clean package -DskipTests
```

This produces `target/cloudflare-mcp-1.0.0.jar` — a self-contained executable JAR (18 MB).

### 2. Set your API token

```bash
export CLOUDFLARE_API_TOKEN='your-token-here'
```

### 3. Register with Claude Code

```bash
java -jar target/cloudflare-mcp-1.0.0.jar --install
```

Or manually:

```bash
claude mcp add --scope user --transport stdio cloudflare -- \
  java -jar /path/to/cloudflare-mcp-1.0.0.jar
```

### 4. Use

```
> List my Cloudflare zones
> Show DNS records for example.com
> Create an A record pointing app.example.com to 1.2.3.4
> List my Workers scripts
> What firewall rules are configured on my zone?
```

## Filtering

The full Cloudflare API has 2,655 operations. Use filters to expose only what you need:

### By tag (API category)

```bash
# Only DNS and Zone tools
java -jar cloudflare-mcp.jar --include-tags "DNS Records,Zones"

# Everything except deprecated APIs
java -jar cloudflare-mcp.jar --exclude-tags "Access Bookmark applications (Deprecated)"
```

### By path

```bash
# Only zone-related endpoints
java -jar cloudflare-mcp.jar --include-paths "/zones/**"

# Zone and DNS endpoints
java -jar cloudflare-mcp.jar --include-paths "/zones/**" --include-tags "DNS Records,Zones"
```

### By HTTP method

```bash
# Read-only mode — only GET endpoints
java -jar cloudflare-mcp.jar --include-methods "GET"

# No deletes
java -jar cloudflare-mcp.jar --exclude-methods "DELETE"
```

### Common filter presets

| Use case | Flags |
|----------|-------|
| DNS management | `--include-tags "DNS Records,Zones"` |
| Workers development | `--include-tags "Worker Script,Workers KV Namespace,Workers for Platforms"` |
| Security audit | `--include-tags "WAF Managed Rules,Firewall Rules,Account Rulesets,Zone Rulesets"` |
| Read-only browsing | `--include-methods "GET"` |
| Storage (R2/KV/D1) | `--include-tags "R2 Bucket,Workers KV Namespace,D1 Database"` |

## Configuration

| Flag | Description | Default |
|------|-------------|---------|
| `--install` | Register with Claude Code | |
| `--claude-binary` | Custom Claude binary path | `claude` |
| `--include-tags` | Comma-separated tags to include | (all) |
| `--exclude-tags` | Comma-separated tags to exclude | (none) |
| `--include-paths` | Comma-separated path globs to include | (all) |
| `--exclude-paths` | Comma-separated path globs to exclude | (none) |
| `--include-methods` | Comma-separated HTTP methods to include | (all) |
| `--exclude-methods` | Comma-separated HTTP methods to exclude | (none) |
| `--max-requests-per-minute` | Rate limit | `240` |
| `--max-response-length` | Truncate responses beyond this length | `50000` |
| `--connect-timeout` | Connection timeout in seconds | `10` |
| `--request-timeout` | Request timeout in seconds | `30` |

### Environment variables

| Variable | Description | Required |
|----------|-------------|----------|
| `CLOUDFLARE_API_TOKEN` | Cloudflare API token (Bearer auth) | Yes |

## Tool annotations

Every generated tool includes MCP annotations based on the HTTP method:

| HTTP Method | readOnlyHint | destructiveHint | idempotentHint |
|-------------|:---:|:---:|:---:|
| GET, HEAD, OPTIONS | true | false | true |
| POST | false | false | false |
| PUT | false | false | true |
| PATCH | false | false | false |
| DELETE | false | true | true |

This means Claude will treat DELETE operations with appropriate caution and won't hesitate to call GET endpoints for information gathering.

## Security

- **Response sanitization** — All API responses are wrapped in unique cryptographic boundary markers with instructions to treat the content as untrusted data. This defends against prompt injection via API responses.
- **Response truncation** — Responses are truncated at a configurable limit (default 50k chars) to prevent context overflow.
- **Rate limiting** — Sliding window rate limiter (default 240/minute, matching Cloudflare's 1,200/5-minute limit).
- **Timeouts** — Configurable connect timeout (10s) and request timeout (30s) prevent hanging connections.
- **Input validation** — Required parameters are validated before API calls.

## Building from source

```bash
mvn clean verify
```

This compiles, runs all 155 tests, and produces the shaded JAR.

## Project structure

```
src/main/java/com/cloudflare/mcp/
├── CloudflareMcpServer.java   # Entry point, CLI, server bootstrap
├── ServerConfig.java          # CLI args, env var interpolation
├── Install.java               # --install command (Claude Code registration)
├── SpecLoader.java            # Load bundled OpenAPI spec (swagger-parser)
├── ToolGenerator.java         # Generate MCP tools from OpenAPI operations
├── ToolNaming.java            # operationId sanitization, method+path fallback
├── SchemaConverter.java       # OpenAPI Schema → JSON Schema
├── InputSchemaBuilder.java    # Merge params + body into single input schema
├── ToolAnnotationMapper.java  # HTTP method → MCP tool annotations
├── HttpApiClient.java         # Execute HTTP requests to Cloudflare API
├── RequestBuilder.java        # Build HTTP requests with Bearer auth
├── OperationFilter.java       # Include/exclude by tag, path, method
├── ContentSanitizer.java      # Prompt injection defense (boundary markers)
├── ResultHelper.java          # MCP result formatting
└── RateLimiter.java           # Sliding window rate limiter

src/main/resources/
└── cloudflare-openapi.json    # Bundled Cloudflare OpenAPI 3.0.3 spec (8.4 MB)
```

## License

[MIT](LICENSE)
