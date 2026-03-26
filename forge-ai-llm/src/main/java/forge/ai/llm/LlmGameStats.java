package forge.ai.llm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Tracks LLM AI game statistics: API calls, token usage, win rate.
 * Persists per-game summaries to a JSONL file.
 */
public class LlmGameStats {

    private final Path statsFile;

    // Per-game counters (reset each game)
    private int apiCallsThisGame;
    private long inputTokensThisGame;
    private long outputTokensThisGame;
    private long cacheReadTokensThisGame;
    private long totalResponseTimeMs;
    private long gameStartTime;

    // Lifetime counters (in-memory only, rebuilt from file on demand)
    private int totalGames;
    private int totalWins;

    public LlmGameStats(String statsFilePath) {
        this.statsFile = Path.of(statsFilePath.replace("~", System.getProperty("user.home")));
        resetGameCounters();
    }

    public void resetGameCounters() {
        apiCallsThisGame = 0;
        inputTokensThisGame = 0;
        outputTokensThisGame = 0;
        cacheReadTokensThisGame = 0;
        totalResponseTimeMs = 0;
        gameStartTime = System.currentTimeMillis();
    }

    public void recordApiCall(LlmResponse response) {
        apiCallsThisGame++;
        inputTokensThisGame += response.getInputTokens();
        outputTokensThisGame += response.getOutputTokens();
        cacheReadTokensThisGame += response.getCacheReadTokens();
        totalResponseTimeMs += response.getResponseTimeMs();
    }

    public void recordGameEnd(boolean won, String deckName, int opponentCount, int turnCount) {
        totalGames++;
        if (won) totalWins++;

        long gameDurationMs = System.currentTimeMillis() - gameStartTime;

        // Build JSONL entry
        String json = String.format(
            "{\"timestamp\":\"%s\",\"won\":%s,\"deck\":\"%s\",\"opponents\":%d,"
            + "\"turns\":%d,\"apiCalls\":%d,\"inputTokens\":%d,\"outputTokens\":%d,"
            + "\"cacheReadTokens\":%d,\"totalResponseTimeMs\":%d,\"gameDurationMs\":%d}",
            Instant.now().toString(),
            won,
            escapeJson(deckName),
            opponentCount,
            turnCount,
            apiCallsThisGame,
            inputTokensThisGame,
            outputTokensThisGame,
            cacheReadTokensThisGame,
            totalResponseTimeMs,
            gameDurationMs
        );

        // Persist
        try {
            Files.createDirectories(statsFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(statsFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(json);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("[LLM-AI] Failed to write stats: " + e.getMessage());
        }

        // Print summary
        printGameSummary(won, deckName, turnCount);

        // Reset for next game
        resetGameCounters();
    }

    private void printGameSummary(boolean won, String deckName, int turnCount) {
        long avgResponseMs = apiCallsThisGame > 0 ? totalResponseTimeMs / apiCallsThisGame : 0;

        System.out.println("[LLM-AI] ========== GAME STATS ==========");
        System.out.println("[LLM-AI] Result: " + (won ? "WIN" : "LOSS"));
        System.out.println("[LLM-AI] Deck: " + deckName);
        System.out.println("[LLM-AI] Turns: " + turnCount);
        System.out.println("[LLM-AI] API Calls: " + apiCallsThisGame);
        System.out.println("[LLM-AI] Tokens — Input: " + inputTokensThisGame
            + " | Output: " + outputTokensThisGame
            + " | Cache Read: " + cacheReadTokensThisGame);
        System.out.println("[LLM-AI] Avg Response Time: " + avgResponseMs + "ms");
        System.out.println("[LLM-AI] Session Record: " + totalWins + "W / "
            + (totalGames - totalWins) + "L ("
            + (totalGames > 0 ? (totalWins * 100 / totalGames) : 0) + "% win rate)");
        System.out.println("[LLM-AI] ================================");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Accessors for testing
    public int getApiCallsThisGame() { return apiCallsThisGame; }
    public long getInputTokensThisGame() { return inputTokensThisGame; }
    public long getOutputTokensThisGame() { return outputTokensThisGame; }
    public int getTotalGames() { return totalGames; }
    public int getTotalWins() { return totalWins; }
}
