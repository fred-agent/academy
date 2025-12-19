package fredlab.dev.samples.a2a;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.stream.Collectors;
import java.time.Duration;

@Service
public class AgentService {

    private final ChatClient chatClient;
    private static final String BRIEF_SKILL = "openai.brief";
    private static final String RESEARCH_SKILL = "openai.research";

    // 1. Define the structure you want the AI to follow
    public record AgentResponse(String title, List<String> bulletPoints) {
        public String toMarkdown() {
            String bullets = bulletPoints.stream()
                    .map(point -> "- " + point.trim())
                    .collect(Collectors.joining("\n"));
            return "## " + title + "\n" + bullets;
        }
    }

    public AgentService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String ask(String userText, String skillId) {
        String skill = resolvedSkill(skillId);

        // Brief skill: structured bullets; Research skill: richer narrative Markdown
        if (RESEARCH_SKILL.equals(skill)) {
            return this.chatClient.prompt()
                    .system(getSystemInstructions(skill))
                    .user(userText)
                    .call()
                    .content();
        }

        AgentResponse response = this.chatClient.prompt()
                .system(getSystemInstructions(skill))
                .user(userText)
                .call()
                .entity(AgentResponse.class);

        return response != null ? response.toMarkdown() : "Error: No response";
    }

    public Flux<String> stream(String userText, String skillId) {
        /* Note: .entity() doesn't support streaming directly in the same way.
           For a demo, if you need streaming Markdown, it is best to use a 
           standard String stream but keep the system prompt very strict.
        */
        return this.chatClient.prompt()
                .system(getSystemInstructions(skillId))
                .user(userText)
                .stream()
                .content()
                // Buffer tokens to avoid mid-word/mid-URL chunks; larger batches for cleaner rendering
                .bufferTimeout(
                        RESEARCH_SKILL.equals(resolvedSkill(skillId)) ? 80 : 120,
                        Duration.ofMillis(350)
                )
                .map(chunks -> String.join("", chunks));
    }

    private String getSystemInstructions(String skillId) {
        String skill = resolvedSkill(skillId);
        return switch (skill) {
            case RESEARCH_SKILL ->
                    """
                    Act as a researcher. Write Markdown with:
                    - A single line title starting with '## '.
                    - 3-4 sections, each starting with '###', each containing 3-5 full sentences (short paragraphs).
                    - Avoid bullets unless a final 2-3 bullet summary at the end.
                    - Provide depth, trade-offs, and concrete examples. Target 220-320 words.
                    - Add a final section titled '### References' with 3-8 bullet links or citations for further reading.
                    Stay focused; do not repeat the user's question.
                    """;
            case BRIEF_SKILL ->
                    """
                    Respond as Markdown with a short title ('## ') and 3-5 concise bullet points.
                    Total length under 90 words. Do not echo the question.
                    """;
            default ->
                    """
                    Respond as Markdown with a short title ('## ') and 3-5 concise bullet points.
                    Total length under 90 words. Do not echo the question.
                    """;
        };
    }

    private String resolvedSkill(String skillId) {
        return (skillId == null || skillId.isBlank()) ? BRIEF_SKILL : skillId;
    }
}
