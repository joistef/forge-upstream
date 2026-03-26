package forge.ai.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LlmResponseParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern BLOCK_PATTERN = Pattern.compile("(\\d+)\\s*->\\s*(\\d+)");

    /**
     * Parse a single action choice from LLM response.
     * Returns -1 for PASS.
     * Throws LlmParseException if unparseable.
     */
    public int parseActionChoice(String response, int maxIndex) {
        if (response == null || response.trim().isEmpty()) {
            throw new LlmParseException("Empty response");
        }

        String cleaned = response.trim().toUpperCase();

        if (cleaned.equals("PASS") || cleaned.startsWith("PASS")) {
            return -1;
        }

        Matcher m = NUMBER_PATTERN.matcher(cleaned);
        if (m.find()) {
            int idx = Integer.parseInt(m.group());
            if (idx >= 0 && idx < maxIndex) {
                return idx;
            }
            throw new LlmParseException("Index " + idx + " out of bounds (max " + maxIndex + ")");
        }

        throw new LlmParseException("Could not parse action from: " + response);
    }

    /**
     * Parse multiple choices (for attackers/blockers).
     * Returns empty list for NONE.
     */
    public List<Integer> parseMultipleChoices(String response, int maxIndex) {
        if (response == null || response.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleaned = response.trim().toUpperCase();

        if (cleaned.equals("NONE") || cleaned.startsWith("NONE")) {
            return Collections.emptyList();
        }

        List<Integer> result = new ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(cleaned);
        while (m.find()) {
            int idx = Integer.parseInt(m.group());
            if (idx >= 0 && idx < maxIndex && !result.contains(idx)) {
                result.add(idx);
            }
        }
        return result;
    }

    /**
     * Parse block assignments: "blocker_idx->attacker_idx" pairs.
     * Returns map of blocker index -> attacker index.
     */
    public Map<Integer, Integer> parseBlockAssignments(String response, int maxBlockers, int maxAttackers) {
        if (response == null || response.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        String cleaned = response.trim().toUpperCase();

        if (cleaned.equals("NONE") || cleaned.startsWith("NONE")) {
            return Collections.emptyMap();
        }

        Map<Integer, Integer> result = new HashMap<>();
        Matcher m = BLOCK_PATTERN.matcher(cleaned);
        while (m.find()) {
            int blocker = Integer.parseInt(m.group(1));
            int attacker = Integer.parseInt(m.group(2));
            if (blocker >= 0 && blocker < maxBlockers && attacker >= 0 && attacker < maxAttackers) {
                result.put(blocker, attacker);
            }
        }
        return result;
    }

    public static class LlmParseException extends RuntimeException {
        public LlmParseException(String message) {
            super(message);
        }
    }
}
