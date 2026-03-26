package forge.ai.llm;

/**
 * Encapsulates an LLM API response with usage metrics.
 */
public class LlmResponse {
    private final String text;
    private final long inputTokens;
    private final long outputTokens;
    private final long cacheReadTokens;
    private final long cacheCreationTokens;
    private final long responseTimeMs;

    public LlmResponse(String text, long inputTokens, long outputTokens,
                        long cacheReadTokens, long cacheCreationTokens,
                        long responseTimeMs) {
        this.text = text;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.cacheCreationTokens = cacheCreationTokens;
        this.responseTimeMs = responseTimeMs;
    }

    public String getText() { return text; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getCacheReadTokens() { return cacheReadTokens; }
    public long getCacheCreationTokens() { return cacheCreationTokens; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public long getTotalTokens() { return inputTokens + outputTokens; }
}
