# 01-chatbot

Minimal A2A chatbot sample that forwards prompts to OpenAI via Spring AI and returns responses to the caller.
It supports both JSON-RPC replies and streaming (SSE) replies.

## What this sample does

- Exposes the A2A agent card at `/.well-known/agent-card.json`
- Implements `message/send` (single response) on `POST /`
- Implements `message/stream` (SSE streaming) on `POST /message/stream`
- Uses Spring AI + OpenAI for remote chat completions
- Secured by default with JWT (OIDC issuer configured via `application.properties`)

## Configuration

Set the OpenAI key and (optionally) the model:

```bash
export SPRING_AI_OPENAI_API_KEY=your-key
export SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=gpt-4o-mini
```

If you keep auth enabled (default), set your issuer URI in `src/main/resources/application.properties`:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/your-realm
```

To disable auth for local testing, set `app.security.enabled=false` or use the Makefile target below.

## Makefile targets (recommended)

```bash
make build
make test
make run              # PORT=9999 by default
make run-secured      # app.security.enabled=true
make run-unsecured    # app.security.enabled=false

make curl-send        # JSON-RPC message/send
make curl-stream      # JSON-RPC message/stream over SSE
make curl-send-pretty
make curl-stream-pretty

make clean
make stop-gradle
```

You can tweak the test prompt and base URL:

```bash
PROMPT="Explain HTTP streaming in 4 short paragraphs." PORT=9999 make curl-stream
```

For more information about the overall repository structure and goals, see the top-level `README.md`.
