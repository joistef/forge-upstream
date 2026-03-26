package forge.ai.llm;

import forge.ai.LobbyPlayerAi;
import forge.game.Game;
import forge.game.player.Player;

import java.util.Collections;

/**
 * Factory for creating LLM AI players. Not used directly in MVP
 * (reflection-based hook in LobbyPlayerAi is used instead),
 * but available for future direct integration.
 */
public class LobbyPlayerLlmAi extends LobbyPlayerAi {

    private final LlmConfig config;

    public LobbyPlayerLlmAi(String name, LlmConfig config) {
        super(name, Collections.emptySet());
        this.config = config;
    }

    public LlmConfig getLlmConfig() {
        return config;
    }
}
