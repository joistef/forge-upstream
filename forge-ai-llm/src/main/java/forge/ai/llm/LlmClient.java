package forge.ai.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;

import java.time.Duration;

public class LlmClient {
    private static LlmClient instance;
    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;
    private final LlmConfig config;

    private LlmClient(LlmConfig config) {
        this.config = config;
        this.client = AnthropicOkHttpClient.builder()
            .apiKey(config.getApiKey())
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .maxRetries(1)
            .build();
        this.model = config.getModel();
        this.maxTokens = config.getMaxTokens();
    }

    public static synchronized LlmClient getInstance(LlmConfig config) {
        if (instance == null) {
            instance = new LlmClient(config);
        }
        return instance;
    }

    public static synchronized void reset() {
        instance = null;
    }

    public String query(String systemPrompt, String userMessage) {
        if (config.isLogPrompts()) {
            System.out.println("[LLM-AI] === PROMPT ===");
            System.out.println("[LLM-AI] System: " + systemPrompt.substring(0, Math.min(200, systemPrompt.length())) + "...");
            System.out.println("[LLM-AI] User: " + userMessage);
            System.out.println("[LLM-AI] === END PROMPT ===");
        }

        MessageCreateParams params = MessageCreateParams.builder()
            .system(systemPrompt)
            .maxTokens(maxTokens)
            .addUserMessage(userMessage)
            .model(model)
            .build();

        Message response = client.messages().create(params);

        String result = response.content().stream()
            .filter(ContentBlock::isText)
            .map(block -> block.asText().text())
            .findFirst()
            .orElse("");

        if (config.isLogResponses()) {
            System.out.println("[LLM-AI] Response: " + result);
        }

        return result;
    }
}
