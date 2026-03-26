package forge.ai.llm;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class LlmConfig {
    private final String apiKey;
    private final String model;
    private final long maxTokens;
    private final int timeoutSeconds;
    private final int maxCallsPerTurn;
    private final int maxGameStateChars;
    private final boolean logPrompts;
    private final boolean logResponses;

    private LlmConfig(String apiKey, String model, long maxTokens, int timeoutSeconds,
                       int maxCallsPerTurn, int maxGameStateChars,
                       boolean logPrompts, boolean logResponses) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.maxCallsPerTurn = maxCallsPerTurn;
        this.maxGameStateChars = maxGameStateChars;
        this.logPrompts = logPrompts;
        this.logResponses = logResponses;
    }

    public static LlmConfig load() {
        Properties props = new Properties();

        // Try loading from properties file
        Path propsPath = Paths.get(System.getProperty("user.home"), ".forge", "llm-ai.properties");
        if (Files.exists(propsPath)) {
            try (FileInputStream fis = new FileInputStream(propsPath.toFile())) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("[LLM-AI] Could not load properties: " + e.getMessage());
            }
        }

        // API key: env var > system property > properties file
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null) apiKey = System.getProperty("anthropic.apiKey");
        if (apiKey == null) apiKey = props.getProperty("llm.api.key");

        return new LlmConfig(
            apiKey,
            getStr(props, "llm.model", "claude-sonnet-4-20250514"),
            getLong(props, "llm.max.tokens", 256),
            getInt(props, "llm.timeout.seconds", 15),
            getInt(props, "llm.max.calls.per.turn", 5),
            getInt(props, "llm.max.game.state.chars", 4000),
            getBool(props, "llm.log.prompts", false),
            getBool(props, "llm.log.responses", false)
        );
    }

    private static String getStr(Properties p, String key, String def) {
        String v = System.getProperty(key);
        if (v != null) return v;
        return p.getProperty(key, def);
    }

    private static int getInt(Properties p, String key, int def) {
        try { return Integer.parseInt(getStr(p, key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static long getLong(Properties p, String key, long def) {
        try { return Long.parseLong(getStr(p, key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean getBool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(getStr(p, key, String.valueOf(def)));
    }

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public long getMaxTokens() { return maxTokens; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxCallsPerTurn() { return maxCallsPerTurn; }
    public int getMaxGameStateChars() { return maxGameStateChars; }
    public boolean isLogPrompts() { return logPrompts; }
    public boolean isLogResponses() { return logResponses; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
