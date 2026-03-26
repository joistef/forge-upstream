package forge.ai.llm;

/**
 * AI personality modes that control strategic behavior via different system prompts.
 */
public enum LlmPersonality {
    DEFAULT("llm-system-prompt.txt"),
    AGGRESSIVE("llm-personality-aggressive.txt"),
    CONTROL("llm-personality-control.txt"),
    POLITICAL("llm-personality-political.txt"),
    CHAOTIC("llm-personality-chaotic.txt");

    private final String resourceFile;

    LlmPersonality(String resourceFile) {
        this.resourceFile = resourceFile;
    }

    public String getResourceFile() {
        return resourceFile;
    }

    public static LlmPersonality fromString(String s) {
        if (s == null || s.isEmpty()) return DEFAULT;
        try {
            return valueOf(s.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            System.err.println("[LLM-AI] Unknown personality '" + s + "', using DEFAULT");
            return DEFAULT;
        }
    }
}
