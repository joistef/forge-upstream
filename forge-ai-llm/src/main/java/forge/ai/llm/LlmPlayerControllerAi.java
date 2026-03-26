package forge.ai.llm;

import forge.ai.ComputerUtilAbility;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.spellability.LandAbility;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LlmPlayerControllerAi extends PlayerControllerAi {

    private final LlmClient llmClient;
    private final GameStateSummarizer summarizer;
    private final LlmPromptBuilder promptBuilder;
    private final LlmResponseParser responseParser;
    private final LlmCallThrottler throttler;
    private final LlmConfig config;
    private final LlmGameStats stats;
    private boolean gameEndRecorded = false;

    public LlmPlayerControllerAi(Game game, Player player, LobbyPlayerAi lp, LlmConfig config) {
        super(game, player, lp);
        this.config = config;
        this.llmClient = LlmClient.getInstance(config);
        this.summarizer = new GameStateSummarizer(config.getMaxGameStateChars());
        this.promptBuilder = new LlmPromptBuilder();
        this.responseParser = new LlmResponseParser();
        this.throttler = new LlmCallThrottler(config.getMaxCallsPerTurn());
        this.stats = new LlmGameStats(config.getStatsFile());

        System.out.println("[LLM-AI] Claude AI initialized for player: " + player.getName());
        System.out.println("[LLM-AI] Model: " + config.getModel());
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        // Check for game end and record stats
        checkGameEnd();

        try {
            Game game = getGame();
            Player player = getPlayer();

            // Check throttle
            if (throttler.shouldSkip(game, player)) {
                return super.chooseSpellAbilityToPlay();
            }

            // Build legal action list
            List<SpellAbility> legalActions = buildLegalActionList(game, player);
            if (legalActions.isEmpty()) {
                return Collections.emptyList();
            }

            // Serialize game state
            String gameState = summarizer.summarize(game, player);

            // Build prompt
            String prompt = promptBuilder.buildPlayPrompt(gameState, legalActions);

            // Query LLM
            LlmResponse response = llmClient.query(promptBuilder.getSystemPrompt(), prompt);
            stats.recordApiCall(response);

            // Parse response
            int chosenIndex = responseParser.parseActionChoice(response.getText(), legalActions.size());

            if (chosenIndex == -1) {
                return Collections.emptyList(); // PASS
            }

            SpellAbility chosen = legalActions.get(chosenIndex);
            System.out.println("[LLM-AI] Chose: " + chosen.getHostCard().getName()
                + (chosen.isSpell() ? " (cast)" : " (activate)"));

            return Collections.singletonList(chosen);

        } catch (Exception e) {
            System.err.println("[LLM-AI] Error in chooseSpellAbilityToPlay, falling back: " + e.getMessage());
            return super.chooseSpellAbilityToPlay();
        }
    }

    @Override
    public void declareAttackers(Player attacker, Combat combat) {
        try {
            Game game = getGame();

            // Get potential attackers
            List<Card> potentialAttackers = getPotentialAttackers(attacker, combat);
            if (potentialAttackers.isEmpty()) {
                return;
            }

            // Serialize combat state
            String gameState = summarizer.summarizeForCombat(game, attacker, combat);

            // Build prompt
            String prompt = promptBuilder.buildAttackPrompt(gameState, potentialAttackers);

            // Query LLM
            LlmResponse response = llmClient.query(promptBuilder.getSystemPrompt(), prompt);
            stats.recordApiCall(response);

            // Parse response
            List<Integer> chosenIndices = responseParser.parseMultipleChoices(response.getText(), potentialAttackers.size());

            if (chosenIndices.isEmpty()) {
                System.out.println("[LLM-AI] Chose: No attackers");
                return;
            }

            // Find a defender (first opponent for simplicity in MVP)
            FCollectionView<Player> opponents = attacker.getOpponents();
            Player defender = null;
            for (Player opp : opponents) {
                if (!opp.hasLost()) {
                    defender = opp;
                    break;
                }
            }
            if (defender == null) return;

            for (int idx : chosenIndices) {
                Card attackingCreature = potentialAttackers.get(idx);
                if (CombatUtil.canAttack(attackingCreature, defender)) {
                    combat.addAttacker(attackingCreature, defender);
                    System.out.println("[LLM-AI] Attacking with: " + attackingCreature.getName());
                }
            }

        } catch (Exception e) {
            System.err.println("[LLM-AI] Error in declareAttackers, falling back: " + e.getMessage());
            super.declareAttackers(attacker, combat);
        }
    }

    @Override
    public void declareBlockers(Player defender, Combat combat) {
        try {
            Game game = getGame();

            // Get attackers aimed at us
            List<Card> attackers = new ArrayList<>(combat.getAttackers());
            if (attackers.isEmpty()) {
                return;
            }

            // Get our potential blockers
            List<Card> potentialBlockers = new ArrayList<>();
            for (Card c : defender.getCreaturesInPlay()) {
                if (CombatUtil.canBlock(c, combat)) {
                    potentialBlockers.add(c);
                }
            }
            if (potentialBlockers.isEmpty()) {
                return;
            }

            // Serialize combat state
            String gameState = summarizer.summarizeForCombat(game, defender, combat);

            // Build prompt
            String prompt = promptBuilder.buildBlockPrompt(gameState, potentialBlockers, attackers);

            // Query LLM
            LlmResponse response = llmClient.query(promptBuilder.getSystemPrompt(), prompt);
            stats.recordApiCall(response);

            // Parse block assignments
            Map<Integer, Integer> assignments = responseParser.parseBlockAssignments(
                response.getText(), potentialBlockers.size(), attackers.size());

            if (assignments.isEmpty()) {
                System.out.println("[LLM-AI] Chose: No blockers");
                return;
            }

            for (Map.Entry<Integer, Integer> entry : assignments.entrySet()) {
                Card blocker = potentialBlockers.get(entry.getKey());
                Card attacker = attackers.get(entry.getValue());
                if (CombatUtil.canBlock(attacker, blocker, combat)) {
                    combat.addBlocker(attacker, blocker);
                    System.out.println("[LLM-AI] " + blocker.getName() + " blocks " + attacker.getName());
                }
            }

        } catch (Exception e) {
            System.err.println("[LLM-AI] Error in declareBlockers, falling back: " + e.getMessage());
            super.declareBlockers(defender, combat);
        }
    }

    private void checkGameEnd() {
        if (gameEndRecorded) return;
        Game game = getGame();
        if (game.isGameOver()) {
            gameEndRecorded = true;
            Player player = getPlayer();
            boolean won = game.getOutcome() != null
                && game.getOutcome().isWinner(player.getRegisteredPlayer());
            String deckName = player.getRegisteredPlayer() != null
                && player.getRegisteredPlayer().getDeck() != null
                ? player.getRegisteredPlayer().getDeck().getName() : "Unknown";
            int opponents = game.getRegisteredPlayers().size() - 1;
            int turns = game.getPhaseHandler().getTurn();
            stats.recordGameEnd(won, deckName, opponents, turns);
        }
    }

    private List<SpellAbility> buildLegalActionList(Game game, Player player) {
        List<SpellAbility> actions = new ArrayList<>();

        // Get playable lands
        CardCollection lands = ComputerUtilAbility.getAvailableLandsToPlay(game, player);
        if (lands != null) {
            for (Card land : lands) {
                for (SpellAbility sa : land.getAllPossibleAbilities(player, false)) {
                    if (sa instanceof LandAbility) {
                        actions.add(sa);
                        break;
                    }
                }
            }
        }

        // Get playable spells and abilities
        CardCollection available = ComputerUtilAbility.getAvailableCards(game, player);
        List<SpellAbility> spellAbilities = ComputerUtilAbility.getSpellAbilities(available, player);
        for (SpellAbility sa : spellAbilities) {
            if (sa.canPlay() && !(sa instanceof LandAbility)) {
                actions.add(sa);
            }
        }

        return actions;
    }

    private List<Card> getPotentialAttackers(Player attacker, Combat combat) {
        List<Card> result = new ArrayList<>();
        CardCollectionView creatures = attacker.getCreaturesInPlay();
        for (Card c : creatures) {
            if (CombatUtil.canAttack(c) && !c.isTapped() && !c.hasSickness()) {
                result.add(c);
            }
        }
        return result;
    }
}
