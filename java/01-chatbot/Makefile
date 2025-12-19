PORT ?= 9999
BASE_URL ?= http://localhost:$(PORT)
PROMPT ?= Explain HTTP streaming in 4 short paragraphs.
.DEFAULT_GOAL := help

.PHONY: help build test run run-secured run-unsecured curl-send curl-stream curl-send-pretty curl-stream-pretty clean stop-gradle

help:
	@echo "Targets:"
	@echo "  build        Run Gradle build"
	@echo "  test         Run tests"
	@echo "  run          Run the app (PORT=9999 by default)"
	@echo "  run-secured  Run with auth enabled (app.security.enabled=true)"
	@echo "  run-unsecured Run with auth disabled (app.security.enabled=false)"
	@echo "  curl-send    Test message/send without auth"
	@echo "  curl-stream  Test message/stream without auth (SSE)"
	@echo "  curl-send-pretty   curl-send piped to jq (colored)"
	@echo "  curl-stream-pretty curl-stream piped to jq (colored)"
	@echo "  clean        Clean build outputs"
	@echo "  stop-gradle  Stop Gradle daemons"

build:
	./gradlew build

test:
	./gradlew test

run:
	./gradlew bootRun --args='--server.port=$(PORT)'

run-secured:
	./gradlew bootRun --args='--server.port=$(PORT) --app.security.enabled=true'

run-unsecured:
	./gradlew bootRun --args='--server.port=$(PORT) --app.security.enabled=false'

curl-send:
	@curl -sS $(BASE_URL)/ \
	  -H 'Content-Type: application/json' \
	  -d '{"jsonrpc":"2.0","id":"1","method":"message/send","params":{"message":{"role":"user","parts":[{"kind":"text","text":"$(PROMPT)"}],"messageId":"m1"},"metadata":{}}}'

curl-stream:
	@curl -sS -N $(BASE_URL)/message/stream \
	  -H 'Content-Type: application/json' \
	  -H 'Accept: text/event-stream' \
	  -d '{"jsonrpc":"2.0","id":"1","method":"message/stream","params":{"message":{"role":"user","parts":[{"kind":"text","text":"$(PROMPT)"}],"messageId":"m1"},"metadata":{}}}'

curl-send-pretty:
	@$(MAKE) -s curl-send | jq -C .

curl-stream-pretty:
	@$(MAKE) -s curl-stream | sed -u -n 's/^data:[[:space:]]*//p' | jq -C --unbuffered .

clean:
	./gradlew clean

stop-gradle:
	./gradlew --stop
