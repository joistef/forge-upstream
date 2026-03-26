package forge.ai;

import java.util.Set;

import forge.LobbyPlayer;
import forge.game.Game;
import forge.game.player.IGameEntitiesFactory;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import org.tinylog.Logger;

public class LobbyPlayerAi extends LobbyPlayer implements IGameEntitiesFactory {

    private String aiProfile = "";
    private boolean rotateProfileEachGame;
    private boolean useSimulation;

    public LobbyPlayerAi(String name, Set<AIOption> options) {
        super(name);
        if (options != null && options.contains(AIOption.USE_SIMULATION)) {
            this.useSimulation = true;
        }
    }

    public void setAiProfile(String profileName) {
        Logger.debug("[AI Preferences] " + name + " using profile " + profileName);
        aiProfile = profileName;
    }
    public String getAiProfile() {
        return aiProfile;
    }

    public void setRotateProfileEachGame(boolean rotateProfileEachGame) {
        this.rotateProfileEachGame = rotateProfileEachGame;
    }

    private PlayerControllerAi createControllerFor(Player ai) {
        // LLM AI: when enabled, use reflection to load Claude controller from forge-ai-llm module
        if (Boolean.getBoolean("forge.ai.llm")) {
            try {
                Class<?> configClazz = Class.forName("forge.ai.llm.LlmConfig");
                Object config = configClazz.getMethod("load").invoke(null);
                Class<?> clazz = Class.forName("forge.ai.llm.LlmPlayerControllerAi");
                return (PlayerControllerAi) clazz.getConstructor(
                    Game.class, Player.class, LobbyPlayerAi.class, configClazz
                ).newInstance(ai.getGame(), ai, this, config);
            } catch (Exception e) {
                System.err.println("[LLM-AI] Failed to load LLM controller, using standard AI: " + e.getMessage());
            }
        }
        PlayerControllerAi result = new PlayerControllerAi(ai.getGame(), ai, this);
        result.setUseSimulation(useSimulation);
        return result;
    }

    @Override
    public PlayerController createMindSlaveController(Player master, Player slave) {
        return createControllerFor(slave);
    }

    @Override
    public Player createIngamePlayer(Game game, final int id) {
        Player ai = new Player(getName(), game, id);
        ai.setFirstController(createControllerFor(ai));

        if (rotateProfileEachGame) {
            setAiProfile(AiProfileUtil.getRandomProfile());
        }
        return ai;
    }

    @Override
    public void hear(LobbyPlayer player, String message) { /* Local AI is deaf. */ }
}