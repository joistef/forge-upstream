package forge.ai.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Usage;

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

    public LlmResponse query(String systemPrompt, String userMessage) {
        if (config.isLogPrompts()) {
            System.out.println("[LLM-AI] === PROMPT ===");
            System.out.println("[LLM-AI] System: " + systemPrompt.substring(0, Math.min(200, systemPrompt.length())) + "...");
            System.out.println("[LLM-AI] User: " + userMessage);
            System.out.println("[LLM-AI] === END PROMPT ===");
        }

        long startTime = System.nanoTime();

        MessageCreateParams params = MessageCreateParams.builder()
            .system(systemPrompt)
            .maxTokens(maxTokens)
            .addUserMessage(userMessage)
            .model(model)
            .build();

        Message response = client.messages().create(params);

        long responseTimeMs = (System.nanoTime() - startTime) / 1_000_000;

        String text = response.content().stream()
            .filter(ContentBlock::isText)
            .map(block -> block.asText().text())
            .findFirst()
            .orElse("");

        // Extract usage metrics
        Usage usage = response.usage();
        long inputTokens = usage.inputTokens();
        long outputTokens = usage.outputTokens();
        long cacheReadTokens = usage.cacheReadInputTokens().orElse(0L);
        long cacheCreationTokens = usage.cacheCreationInputTokens().orElse(0L);

        if (config.isLogResponses()) {
            System.out.println("[LLM-AI] Response: " + text
                + " [" + inputTokens + "in/" + outputTokens + "out/"
                + cacheReadTokens + "cached " + responseTimeMs + "ms]");
        }

        return new LlmResponse(text, inputTokens, outputTokens,
            cacheReadTokens, cacheCreationTokens, responseTimeMs);
    }
}
