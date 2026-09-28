# OpenAI provider

FluxGate accepts `model: "mock-model"` for local mock responses and `model: "openai"`
for the configured OpenAI model. Other names return HTTP 400. The public alias
keeps clients from selecting arbitrary upstream models. Existing clients that used
arbitrary model strings with the mock must switch to `mock-model`.

The OpenAI adapter calls `POST https://api.openai.com/v1/responses`. FluxGate's own
endpoint remains `/v1/chat/completions` with its existing `model`/`prompt` contract;
it is not an OpenAI-compatible API.

## Enable locally

In the terminal that will start the backend (zsh), enter your provider key without
putting it in shell history:

```zsh
read -s "OPENAI_API_KEY?OpenAI API key: "
echo
export OPENAI_API_KEY
read "OPENAI_MODEL?OpenAI model ID available to your account: "
export OPENAI_MODEL
export OPENAI_ENABLED=true
docker compose up -d postgres
./mvnw spring-boot:run
```

Use a text-generation model that supports the Responses API. Access and billing
depend on your OpenAI API account. The application fails startup if OpenAI is
enabled without a key or model. OpenAI is disabled by default; in that state its
alias returns HTTP 503 and mock requests still work.

In a second terminal, enter your **FluxGate key**, which is different from the
OpenAI provider key, and send a request:

```zsh
read -s "FLUXGATE_API_KEY?FluxGate API key: "
echo
curl -i http://localhost:8080/v1/chat/completions \
  -H "X-API-Key: $FLUXGATE_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"model":"openai","prompt":"Say hello in one short sentence."}'
```

This makes a real, potentially billable OpenAI API call. The response uses the
gateway's request ID and the public `openai` alias. The database request log records
`provider=openai` and the configured upstream model. Prompts and generated text
are not stored in request-log rows. The gateway sends its request ID upstream as
`X-Client-Request-Id`.

## Current behavior and limits

- Non-streaming text only, with no tools or conversation history.
- Connection timeout: 5 seconds. Whole HTTP-exchange timeout: 30 seconds, including
  receipt of the response body. This does not bound authentication or database logging time.
- Maximum output tokens: 1,024. Responses send `store: false` and `stream: false`.
- No application retries or provider fallback. A timeout does not prove that OpenAI
  stopped processing or that the request was not billed. Cancellation is best effort.
- The gateway still uses its existing process-local, per-key request limit.
- Multiple output text parts are joined. Incomplete responses are rejected rather
  than presenting truncated text as a complete answer. Usage accounting is not implemented.

Timeouts and output limits can be overridden with Spring configuration, for example:

```zsh
export FLUXGATE_OPENAI_CONNECTTIMEOUT=5s
export FLUXGATE_OPENAI_REQUESTTIMEOUT=45s
export FLUXGATE_OPENAI_MAXOUTPUTTOKENS=2048
```

| Failure | HTTP status | Error code |
| --- | --- | --- |
| Unknown gateway model alias | 400 | `unsupported_model` |
| OpenAI disabled | 503 | `upstream_not_configured` |
| Timeout | 504 | `upstream_timeout` |
| OpenAI rate/quota rejection | 503 | `upstream_rate_limited` |
| OpenAI credential rejection | 502 | `upstream_authentication_failed` |
| Transport failure or upstream 5xx | 503 | `upstream_unavailable` |
| Other upstream non-success status | 502 | `upstream_rejected_request` |
| Malformed response or missing text | 502 | `upstream_invalid_response` |
| Incomplete output | 502 | `upstream_incomplete_response` |
| Provider refusal | 422 | `upstream_refusal` |

An upstream 429 is deliberately distinct from FluxGate's own HTTP 429 admission
rejection. Raw upstream error bodies are not forwarded or retained in exceptions.
Provider errors and unknown model requests currently consume the gateway admission
slot, because rate limiting runs before routing and execution.

## Verification

```bash
./mvnw -Dtest=OpenAiProviderClientTest,OpenAiConfigTest,ProviderRoutingServiceTest,RequestLoggingWebMvcTest test
```

The HTTP contract tests use a local server and fake provider credentials. They test
success, malformed output, refusals, upstream errors, connection failure, and a
stalled body. No tests contact OpenAI. A successful automated run does not establish
that a particular real account/key/model combination works; use the manual request
above for that check.

Official contract references:
- [Responses API](https://developers.openai.com/api/reference/cli/resources/responses/methods/create)
- [Text generation and output structure](https://developers.openai.com/api/docs/guides/text)
- [Authentication and request IDs](https://developers.openai.com/api/reference/overview)
