package forge.ai.llm;

/**
 * Quick smoke test - run manually with ANTHROPIC_API_KEY set.
 * Not part of the automated test suite.
 */
public class LlmSmokeTest {
    public static void main(String[] args) {
        System.out.println("=== LLM Smoke Test ===");

        // Test config loading
        LlmConfig config = LlmConfig.load();
        System.out.println("API Key configured: " + config.isConfigured());
        System.out.println("Model: " + config.getModel());

        if (!config.isConfigured()) {
            System.err.println("ERROR: Set ANTHROPIC_API_KEY environment variable");
            System.exit(1);
        }

        // Test LLM client
        LlmClient client = LlmClient.getInstance(config);

        String systemPrompt = "You are an expert Magic: The Gathering player. "
            + "Respond with ONLY a number to select an action, or PASS.";

        String userPrompt = "GAME STATE:\n"
            + "Turn: 3 | Phase: Main1 | Active Player: YOU\n"
            + "\n"
            + "=== YOU (Life: 40) ===\n"
            + "Hand: Lightning Bolt {R}, Goblin Guide {R}, Mountain\n"
            + "Battlefield:\n"
            + "  Lands: Mountain x2 (2 untapped)\n"
            + "Library: 95 cards\n"
            + "\n"
            + "=== Opponent (Life: 40) ===\n"
            + "Hand: 7 cards\n"
            + "Battlefield:\n"
            + "  Creatures: Llanowar Elves (1/1) {Untapped}\n"
            + "  Lands: Forest x2 (2 untapped)\n"
            + "Library: 93 cards\n"
            + "\n"
            + "LEGAL ACTIONS:\n"
            + "0: Play Mountain (Land)\n"
            + "1: Cast Goblin Guide (Cost: {R}) -- Creature 2/2 Haste [2/2]\n"
            + "2: Cast Lightning Bolt (Cost: {R}) -- Deal 3 damage to any target\n"
            + "PASS: Do nothing\n"
            + "\nChoose action:";

        System.out.println("\n--- Sending prompt ---");
        System.out.println(userPrompt);
        System.out.println("--- End prompt ---\n");

        try {
            LlmResponse response = client.query(systemPrompt, userPrompt);
            System.out.println("LLM Response: \"" + response.getText() + "\"");
            System.out.println("Tokens — Input: " + response.getInputTokens()
                + " | Output: " + response.getOutputTokens()
                + " | Cache Read: " + response.getCacheReadTokens()
                + " | Time: " + response.getResponseTimeMs() + "ms");

            // Parse it
            LlmResponseParser parser = new LlmResponseParser();
            int choice = parser.parseActionChoice(response.getText(), 3);
            String[] actions = {"Play Mountain", "Cast Goblin Guide", "Cast Lightning Bolt"};

            if (choice == -1) {
                System.out.println("Parsed: PASS");
            } else {
                System.out.println("Parsed: " + choice + " (" + actions[choice] + ")");
            }

            // Test stats tracking
            LlmGameStats stats = new LlmGameStats(config.getStatsFile());
            stats.recordApiCall(response);
            System.out.println("Stats — API Calls: " + stats.getApiCallsThisGame()
                + " | Input Tokens: " + stats.getInputTokensThisGame());

            System.out.println("\n=== SMOKE TEST PASSED ===");
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
