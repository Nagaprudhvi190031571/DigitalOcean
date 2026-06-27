# LLM Shadow Proxy

A Spring Boot proxy for OpenAI-compatible chat completion APIs. It forwards every request synchronously to a **primary** upstream and returns that response to the client immediately. In parallel, it sends the same request to a **candidate** upstream on a dedicated async executor, compares the responses, and logs any mismatch — without affecting client latency or reliability.

## How it works

```
Client
  │
  ▼
/v1/chat/completions  ──►  Primary upstream  ──►  Response returned to client
  │
  └── (async, fire-and-forget) ──►  Candidate upstream  ──►  Compare & log
```

1. **Primary path (synchronous)** — The proxy forwards the request to the primary LLM and streams the response back to the caller. This is the only path that determines what the client sees.
2. **Shadow path (asynchronous)** — After the primary response is received, the same request is dispatched to the candidate LLM on a bounded thread pool. Failures or slow responses on this path never block or alter the client response.
3. **Comparison** — Responses are compared by assistant message content (`choices[0].message.content`). Full JSON equality is also accepted. Mismatches are logged at `WARN` level with truncated response bodies.

Built-in mock endpoints (`/mock/primary/...` and `/mock/candidate/...`) are included for local development and demos. By default, both upstream URLs point at these mocks on the same server.

## Requirements

- Java 17+
- Maven 3.9+
- Docker (optional, for container builds)
- [doctl](https://docs.digitalocean.com/reference/doctl/how-to/install/) (optional, for DigitalOcean deployment)

## Quick start

```bash
# Run tests
mvn verify

# Start locally (uses built-in mocks)
mvn spring-boot:run
```

Send a request:

```bash
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-mock","messages":[{"role":"user","content":"hello"}]}'
```

Trigger an intentional shadow mismatch (candidate mock returns different content):

```bash
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"shadow_mismatch":true,"messages":[{"role":"user","content":"trigger mismatch"}]}'
```

Check the application logs for `Shadow match` or `Shadow MISMATCH` entries.

## Configuration

All settings can be overridden via environment variables:

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP listen port |
| `LLM_PRIMARY_URL` | `http://localhost:{port}/mock/primary/v1/chat/completions` | Primary upstream URL |
| `LLM_CANDIDATE_URL` | `http://localhost:{port}/mock/candidate/v1/chat/completions` | Candidate upstream URL |
| `LLM_SHADOW_ENABLED` | `true` | Enable or disable shadow traffic |
| `LLM_CONNECT_TIMEOUT` | `5s` | TCP connect timeout for upstream calls |
| `LLM_READ_TIMEOUT` | `30s` | Read timeout for the primary upstream |
| `LLM_SHADOW_READ_TIMEOUT` | `120s` | Read timeout for the candidate upstream |

Example with real upstreams:

```bash
export LLM_PRIMARY_URL=https://api.openai.com/v1/chat/completions
export LLM_CANDIDATE_URL=https://api.openai.com/v1/chat/completions
export LLM_SHADOW_ENABLED=true
mvn spring-boot:run
```

Pass through `Authorization` and other client headers as-is; hop-by-hop headers (`Host`, `Content-Length`, etc.) are stripped before forwarding.

## Observability

| Endpoint | Description |
|---|---|
| `/actuator/health` | Overall health |
| `/actuator/health/liveness` | Liveness probe (used by Docker and App Platform) |
| `/actuator/health/readiness` | Readiness probe |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/prometheus` | Prometheus scrape endpoint |

Shadow comparison counters:

- `llm.shadow.comparison{result="match"}`
- `llm.shadow.comparison{result="mismatch"}`
- `llm.shadow.comparison{result="failure"}`

## Testing

```bash
mvn verify
```

| Test | What it covers |
|---|---|
| `ComparisonServiceTest` | Response matching logic (equal content, mismatches) |
| `ProxyServiceHeaderTest` | Header forwarding (keeps auth/custom, skips hop-by-hop) |
| `ShadowIsolationIntegrationTest` | End-to-end shadow isolation with WireMock upstreams |

## Docker

Build and run locally:

```bash
docker build -t llm-proxy .
docker run -p 8080:8080 llm-proxy
```

The image uses a multi-stage build (Maven + JRE 17 Alpine) and includes a health check against `/actuator/health/liveness`.

## Deploy to DigitalOcean

This project includes an [App Platform](https://docs.digitalocean.com/products/app-platform/) spec at `.do/app.yaml`. App Platform builds from the `Dockerfile` and redeploys automatically on every push to `main`.

### CLI deployment

```bash
# Authenticate (one-time)
doctl auth init

# Deploy or update
./scripts/deploy-digitalocean.sh
```

### Web UI deployment

1. Go to [DigitalOcean Apps](https://cloud.digitalocean.com/apps) and click **Create App**.
2. Connect the GitHub repository and select the `main` branch.
3. Confirm the **Dockerfile** build method is detected.
4. Set the health check path to `/actuator/health/liveness`.
5. Add environment variables for real upstream URLs if needed (mark secrets as encrypted).
6. Deploy.

### Production environment variables

For a live deployment against real LLM APIs, set these in the App Platform console:

```
LLM_PRIMARY_URL=https://api.openai.com/v1/chat/completions
LLM_CANDIDATE_URL=<your-candidate-endpoint>
LLM_SHADOW_ENABLED=true
```

Without these, the app falls back to its built-in mock endpoints — useful for smoke testing the deployment itself.

## Project structure

```
src/main/java/com/proxy/llm/
├── Application.java              # Spring Boot entry point
├── config/
│   ├── AsyncConfig.java          # Bounded thread pool for shadow calls
│   ├── LlmProperties.java        # Typed configuration with validation
│   └── RestClientConfig.java     # Primary and shadow RestClient beans
├── controller/
│   ├── ProxyController.java      # POST /v1/chat/completions
│   └── MockLlmController.java    # Built-in mock upstreams
├── model/
│   ├── LlmForwardResponse.java
│   └── ShadowRequestContext.java
└── service/
    ├── ProxyService.java         # Synchronous primary forwarding
    ├── ShadowService.java        # Async candidate forwarding
    └── ComparisonService.java    # Response comparison and logging
```

## CI

GitHub Actions runs `mvn verify` on every push and pull request to `main` (see `.github/workflows/ci.yml`).
