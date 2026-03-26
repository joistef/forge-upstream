package forge.ai.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling window of past LLM exchanges within a game.
 * Stores condensed summaries (turn + phase + chosen action),
 * not full game states. Provides strategic continuity across turns.
 */
public class LlmConversationHistory {

    private final List<HistoryEntry> entries = new ArrayList<>();
    private final int maxEntries;
    private final int maxTotalChars;

    public LlmConversationHistory(int maxEntries, int maxTotalChars) {
        this.maxEntries = maxEntries;
        this.maxTotalChars = maxTotalChars;
    }

    /**
     * Record an exchange: what turn/phase it was, what was asked, what was chosen.
     * Stores a condensed summary, not the full prompt.
     */
    public void addExchange(int turn, String phase, String chosenAction) {
        String summary = "Turn " + turn + " " + phase + ": " + chosenAction;
        entries.add(new HistoryEntry(turn, phase, summary));
        evict();
    }

    /**
     * Get the history as a single text block for injection into prompts.
     */
    public String toPromptSection() {
        if (entries.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("RECENT DECISIONS:\n");
        for (HistoryEntry entry : entries) {
            sb.append("  ").append(entry.summary).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get the history formatted as alternating user/assistant message pairs
     * for multi-turn API calls.
     */
    public List<String[]> toMessagePairs() {
        List<String[]> pairs = new ArrayList<>();
        for (HistoryEntry entry : entries) {
            // User message: condensed game state reference
            // Assistant message: the chosen action
            pairs.add(new String[]{
                "Turn " + entry.turn + " " + entry.phase + " — What do you play?",
                entry.summary.contains("Chose") ? entry.summary.substring(entry.summary.indexOf("Chose")) : entry.summary
            });
        }
        return pairs;
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private void evict() {
        // Remove oldest entries if over max count
        while (entries.size() > maxEntries) {
            entries.remove(0);
        }

        // Remove oldest entries if total chars exceeds limit
        int totalChars = entries.stream().mapToInt(e -> e.summary.length()).sum();
        while (totalChars > maxTotalChars && !entries.isEmpty()) {
            totalChars -= entries.remove(0).summary.length();
        }
    }

    private static class HistoryEntry {
        final int turn;
        final String phase;
        final String summary;

        HistoryEntry(int turn, String phase, String summary) {
            this.turn = turn;
            this.phase = phase;
            this.summary = summary;
        }
    }
}
