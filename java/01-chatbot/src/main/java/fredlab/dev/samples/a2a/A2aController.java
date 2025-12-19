package fredlab.dev.samples.a2a;

import fredlab.dev.samples.a2a.model.A2aTypes;
import fredlab.dev.samples.a2a.model.JsonRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class A2aController {

  private static final Logger log = LoggerFactory.getLogger(A2aController.class);

  private final AgentService agentService;

  public A2aController(AgentService agentService) {
    this.agentService = agentService;
  }

  /**
   * Public A2A Agent Card (well-known URI).
   * Recommended location in the A2A spec: /.well-known/agent-card.json.
   */
  @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public A2aTypes.AgentCard agentCard() {
    log.info("Serving agent card");
    return buildAgentCard();
  }

  /**
   * JSON-RPC endpoint for A2A methods.
   * Your client typically uses the AgentCard.url for the RPC endpoint.
   */
  @PostMapping(path = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<JsonRpc.Response> jsonRpc(@RequestBody JsonRpc.Request req,
                                        @AuthenticationPrincipal Jwt jwt) {
    if (req == null || req.method() == null) {
      return Mono.just(JsonRpc.Response.err(null, -32600, "Invalid Request"));
    }

    String username = jwt != null ? jwt.getClaimAsString("preferred_username") : "anonymous";
    String subject = jwt != null ? jwt.getSubject() : "unknown";
    log.info("[AUTH] Received call from user: {} (sub: {})", username, subject);
    log.info("[A2A-PROTOCOL] Received JSON-RPC request: {}", req);

    JsonRpc.Response response = switch (req.method()) {
      case "message/send" -> handleMessageSend(req);
      case "agent/getAuthenticatedExtendedCard" ->
          JsonRpc.Response.err(req.id(), -32007, "AuthenticatedExtendedCardNotConfiguredError");
      case "message/stream" ->
          JsonRpc.Response.err(req.id(), -32601, "Streaming not supported on this endpoint. Use /message/stream.");
      default -> JsonRpc.Response.err(req.id(), -32601, "Method not found: " + req.method());
    };

    log.info("[A2A-PROTOCOL] Sending JSON-RPC response: {}", response);
    return Mono.just(response);
  }

  /**
   * Streaming endpoint for message/stream (SSE).
   */
  @PostMapping(path = "/message/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<Object>> messageStream(@RequestBody(required = false) @Nullable JsonRpc.Request req,
                                                     @AuthenticationPrincipal Jwt jwt) {
    String username = jwt != null ? jwt.getClaimAsString("preferred_username") : "anonymous";
    String subject = jwt != null ? jwt.getSubject() : "unknown";
    log.info("[AUTH] Received streaming call from user: {} (sub: {})", username, subject);
    log.info("[A2A-PROTOCOL] Received streaming request: {}", req);

    if (req == null || !"message/stream".equals(req.method())) {
      Object requestId = req == null ? null : req.id();
      return Flux.just(ServerSentEvent.<Object>builder(JsonRpc.Response.err(requestId, -32600, "Expected JSON-RPC method message/stream")).build());
    }

    String taskId = UUID.randomUUID().toString();
    String contextId = UUID.randomUUID().toString();
    Object requestId = req.id() != null ? req.id() : UUID.randomUUID().toString();
    String userText = extractUserText(req.params());
    String skillId = extractSkillId(req.params());
    log.info("[BUSINESS] message/stream: id={} task={} ctx={} skill={} text='{}'", requestId, taskId, contextId, resolvedSkill(skillId), userText);

    var userMessage = new A2aTypes.Message(
        "user",
        List.of(new A2aTypes.TextPart("text", userText)),
        UUID.randomUUID().toString(),
        taskId,
        contextId
    );

    var submittedTask = new A2aTypes.Task(
        taskId,
        contextId,
        new A2aTypes.TaskStatus("submitted"),
        null,
        List.of(userMessage),
        A2aTypes.KIND_TASK,
        Map.of()
    );

    var workingUpdate1 = new A2aTypes.TaskStatusUpdateEvent(
        taskId,
        contextId,
        new A2aTypes.TaskStatus(
            "working",
            new A2aTypes.Message(
                "agent",
                List.of(new A2aTypes.TextPart("text", "Thinking about your request...")),
                UUID.randomUUID().toString(),
                taskId,
                contextId
            )
        ),
        false
    );

    var workingUpdate2 = new A2aTypes.TaskStatusUpdateEvent(
        taskId,
        contextId,
        new A2aTypes.TaskStatus(
            "working",
            new A2aTypes.Message(
                "agent",
                List.of(new A2aTypes.TextPart("text", "Drafting the response in chunks...")),
                UUID.randomUUID().toString(),
                taskId,
                contextId
            )
        ),
        false
    );

    String artifactId = UUID.randomUUID().toString();
    var first = new AtomicBoolean(true);

    Flux<ServerSentEvent<Object>> chunks = agentService.stream(userText, skillId)
        .filter(chunk -> chunk != null && !chunk.isBlank())
        .map(chunk -> {
          Boolean append = first.getAndSet(false) ? null : Boolean.TRUE;
          var artifactUpdate = new A2aTypes.TaskArtifactUpdateEvent(
              taskId,
              contextId,
              new A2aTypes.Artifact(
                  artifactId,
                  "answer",
                  List.of(new A2aTypes.TextPart("text", chunk))
              ),
              append,
              null,
              null
          );
          return ServerSentEvent.<Object>builder(JsonRpc.Response.ok(requestId, artifactUpdate)).build();
        });

    var completedStatus = new A2aTypes.TaskStatusUpdateEvent(
        taskId,
        contextId,
        new A2aTypes.TaskStatus("completed"),
        true
    );

    return Flux.concat(
        Flux.just(ServerSentEvent.<Object>builder(JsonRpc.Response.ok(requestId, submittedTask)).build()),
        Flux.just(ServerSentEvent.<Object>builder(JsonRpc.Response.ok(requestId, workingUpdate1)).build()),
        Flux.just(ServerSentEvent.<Object>builder(JsonRpc.Response.ok(requestId, workingUpdate2)).build()),
        chunks,
        Flux.just(ServerSentEvent.<Object>builder(JsonRpc.Response.ok(requestId, completedStatus)).build())
    ).onErrorResume(ex -> Flux.just(ServerSentEvent.<Object>builder(
        JsonRpc.Response.err(requestId, -32000, "Streaming error: " + ex.getMessage())
    ).build()));
  }

  private JsonRpc.Response handleMessageSend(JsonRpc.Request req) {
    String taskId = UUID.randomUUID().toString();
    String contextId = UUID.randomUUID().toString();
    Object requestId = req.id() != null ? req.id() : UUID.randomUUID().toString();
    String userText = extractUserText(req.params());
    String skillId = extractSkillId(req.params());

    log.info("[BUSINESS] message/send: id={} task={} ctx={} skill={} text='{}'", requestId, taskId, contextId, resolvedSkill(skillId), userText);

    var completedTask = buildCompletedTask(taskId, contextId, userText, skillId);
    return JsonRpc.Response.ok(requestId, completedTask);
  }

  private A2aTypes.AgentCard buildAgentCard() {
    var briefSkill = new A2aTypes.Skill(
        "openai.brief",
        "Quick bullets",
        "Fast, concise answers in Markdown bullets (90 words max).",
        List.of("ai", "chat", "openai", "brief"),
        List.of("In 3 bullets, what is HTTP streaming?"),
        List.of("text/plain"),
        List.of("text/markdown")
    );

    var researchSkill = new A2aTypes.Skill(
        "openai.research",
        "Researcher",
        "Longer, structured Markdown with sections and richer detail; streams in multiple chunks.",
        List.of("ai", "chat", "openai", "research"),
        List.of("Deep dive: pros/cons of HTTP streaming vs WebSockets."),
        List.of("text/plain"),
        List.of("text/markdown")
    );

    return new A2aTypes.AgentCard(
        "0.3.0",
        "Java A2A OpenAI Agent",
        "Minimal A2A server that forwards user prompts to OpenAI via Spring AI (streaming supported).",
        "http://localhost:9999/",
        "JSONRPC",
        new A2aTypes.Capabilities(true, false, false),
        List.of("text/plain"),
        List.of("text/markdown"),
        List.of(briefSkill, researchSkill),
        false
    );
  }

  private A2aTypes.Task buildCompletedTask(String taskId, String contextId, String userText, String skillId) {
    String answer = agentService.ask(userText, skillId);

    var artifact = new A2aTypes.Artifact(
        UUID.randomUUID().toString(),
        "answer",
        List.of(new A2aTypes.TextPart("text", answer))
    );

    var userMessage = new A2aTypes.Message(
        "user",
        List.of(new A2aTypes.TextPart("text", userText == null ? "" : userText)),
        UUID.randomUUID().toString(),
        taskId,
        contextId
    );

    var assistantMessage = new A2aTypes.Message(
        "agent",
        List.of(new A2aTypes.TextPart("text", answer)),
        UUID.randomUUID().toString(),
        taskId,
        contextId
    );

    return new A2aTypes.Task(
        taskId,
        contextId,
        new A2aTypes.TaskStatus("completed"),
        List.of(artifact),
        List.of(userMessage, assistantMessage),
        A2aTypes.KIND_TASK,
        Map.of("skillId", resolvedSkill(skillId))
    );
  }

  private String extractUserText(Object paramsObj) {
    if (!(paramsObj instanceof Map<?, ?> params)) return "";
    Object messageObj = params.get("message");
    if (!(messageObj instanceof Map<?, ?> msg)) return "";
    Object partsObj = msg.get("parts");
    if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) return "";
    Object first = parts.get(0);
    if (!(first instanceof Map<?, ?> part)) return "";
    Object text = part.get("text");
    return text == null ? "" : text.toString();
  }

  private String extractSkillId(Object paramsObj) {
    if (!(paramsObj instanceof Map<?, ?> params)) return null;
    Object metadataObj = params.get("metadata");
    if (!(metadataObj instanceof Map<?, ?> metadata)) return null;
    Object skillId = metadata.get("skillId");
    return skillId == null ? null : skillId.toString();
  }

  private String resolvedSkill(String skillId) {
    return (skillId == null || skillId.isBlank()) ? "openai.brief" : skillId;
  }
}
