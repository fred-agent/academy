package fredlab.dev.samples.a2a.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class A2aTypes {

  public static final String KIND_MESSAGE = "message";
  public static final String KIND_TASK = "task";
  public static final String KIND_STATUS_UPDATE = "status-update";
  public static final String KIND_ARTIFACT_UPDATE = "artifact-update";

  // ---- Agent Card ----
  public record AgentCard(
      String protocolVersion,
      String name,
      String description,
      String url,
      String preferredTransport,
      Capabilities capabilities,
      List<String> defaultInputModes,
      List<String> defaultOutputModes,
      List<Skill> skills,
      Boolean supportsAuthenticatedExtendedCard
  ) {}

  public record Capabilities(Boolean streaming, Boolean pushNotifications, Boolean stateTransitionHistory) {}

  public record Skill(
      String id,
      String name,
      String description,
      List<String> tags,
      List<String> examples,
      List<String> inputModes,
      List<String> outputModes
  ) {}

  // ---- Message / Task ----
  public record Message(String role, List<Part> parts, String messageId, String taskId, String contextId, String kind) {
    public Message(String role, List<Part> parts, String messageId, String taskId, String contextId) {
      this(role, parts, messageId, taskId, contextId, KIND_MESSAGE);
    }
  }

  public sealed interface Part permits TextPart {}
  public record TextPart(String kind, String text) implements Part {}

  public record MessageSendParams(Message message, Map<String, Object> metadata) {}

  public record Task(
      String id,
      String contextId,
      TaskStatus status,
      List<Artifact> artifacts,
      List<Message> history,
      String kind,
      Map<String, Object> metadata
  ) {
    public Task(String id, String contextId, TaskStatus status, List<Artifact> artifacts, List<Message> history) {
      this(id, contextId, status, artifacts, history, KIND_TASK, Map.of());
    }
  }

  public record TaskStatus(String state, Message message) {
    public TaskStatus(String state) { this(state, null); }
  }

  public record Artifact(String artifactId, String name, List<Part> parts) {}

  public record TaskStatusUpdateEvent(
      String taskId,
      String contextId,
      TaskStatus status,
      @JsonProperty("final") Boolean isFinal,
      Map<String, Object> metadata,
      String kind
  ) {
    public TaskStatusUpdateEvent(String taskId, String contextId, TaskStatus status, Boolean isFinal) {
      this(taskId, contextId, status, isFinal, null, KIND_STATUS_UPDATE);
    }
  }

  public record TaskArtifactUpdateEvent(
      String taskId,
      String contextId,
      Artifact artifact,
      Boolean append,
      Boolean lastChunk,
      @JsonProperty("final") Boolean isFinal,
      Map<String, Object> metadata,
      String kind
  ) {
    public TaskArtifactUpdateEvent(String taskId, String contextId, Artifact artifact, Boolean append, Boolean lastChunk, Boolean isFinal) {
      this(taskId, contextId, artifact, append, lastChunk, isFinal, null, KIND_ARTIFACT_UPDATE);
    }
  }
}
