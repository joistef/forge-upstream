package forge.ai.llm;

import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilMana;
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
    private final LlmConversationHistory history;
    private final LlmTurnPlanner planner;
    private boolean gameEndRecorded = false;

    public LlmPlayerControllerAi(Game game, Player player, LobbyPlayerAi lp, LlmConfig config) {
        super(game, player, lp);
        this.config = config;
        this.llmClient = LlmClient.getInstance(config);
        this.summarizer = new GameStateSummarizer(config.getMaxGameStateChars());
        // Each AI player gets its own personality — RANDOM assigns one randomly
        LlmPersonality personality = LlmPersonality.fromString(config.getPersonality());
        if (personality == LlmPersonality.RANDOM) {
            personality = LlmPersonality.pickRandom();
        }
        this.promptBuilder = new LlmPromptBuilder(personality);
        this.responseParser = new LlmResponseParser();
        this.throttler = new LlmCallThrottler(config.getMaxCallsPerTurn(),
            config.getReactiveMaxCalls(), config.isReactiveEnabled());
        this.planner = new LlmTurnPlanner(config.getPlanningHorizon());
        this.stats = new LlmGameStats(config.getStatsFile());
        this.history = new LlmConversationHistory(config.getHistoryMaxEntries(), config.getHistoryMaxChars());

        // Analyze deck and set brief in prompt builder
        try {
            String deckBrief = DeckAnalyzer.generateBrief(player, config.getDeckProfileDir());
            promptBuilder.setDeckBrief(deckBrief);
            System.out.println("[LLM-AI] Deck brief: " + deckBrief);
        } catch (Exception e) {
            System.err.println("[LLM-AI] Deck analysis failed: " + e.getMessage());
        }

        System.out.println("[LLM-AI] Claude AI initialized for player: " + player.getName());
        System.out.println("[LLM-AI] Model: " + config.getModel());
        System.out.println("[LLM-AI] Personality: " + personality);
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

            // Multi-turn planning: create/refresh plan if needed
            int turn = game.getPhaseHandler().getTurn();
            if (config.isPlanningEnabled()
                    && game.getPhaseHandler().getPhase() == forge.game.phase.PhaseType.MAIN1
                    && game.getPhaseHandler().getPlayerTurn() == player) {
                int life = player.getLife();
                int creatures = player.getCreaturesInPlay().size();
                if (planner.needsNewPlan(turn, life, creatures)) {
                    try {
                        String planPrompt = promptBuilder.buildPlanningPrompt(gameState);
                        LlmResponse planResponse = llmClient.query(promptBuilder.getSystemPrompt(), planPrompt);
                        stats.recordApiCall(planResponse);
                        planner.setPlan(planResponse.getText(), turn, life, creatures);
                    } catch (Exception e) {
                        System.err.println("[LLM-AI] Planning failed: " + e.getMessage());
                    }
                }
            }

            // Inject conversation history
            String historySection = history.toPromptSection();
            String fullState = historySection.isEmpty() ? gameState : gameState + "\n" + historySection;

            // Build prompt — use reactive prompt if responding to opponent's spell
            String prompt;
            if (throttler.isReactiveScenario(game, player)) {
                String stackDesc = summarizer.summarizeStack(game);
                prompt = promptBuilder.buildReactivePrompt(fullState, legalActions, stackDesc);
            } else {
                prompt = promptBuilder.buildPlayPrompt(fullState, legalActions);
            }

            // Inject current plan if available
            if (planner.hasPlan()) {
                prompt = promptBuilder.injectPlan(prompt, planner.getCurrentPlan());
            }

            // Query LLM
            LlmResponse response = llmClient.query(promptBuilder.getSystemPrompt(), prompt);
            stats.recordApiCall(response);

            // Parse response
            int chosenIndex = responseParser.parseActionChoice(response.getText(), legalActions.size());

            String phase = game.getPhaseHandler().getPhase().toString();

            if (chosenIndex == -1) {
                history.addExchange(turn, phase, "Chose PASS");
                return Collections.emptyList();
            }

            SpellAbility chosen = legalActions.get(chosenIndex);
            String actionDesc = chosen.getHostCard().getName()
                + (chosen.isSpell() ? " (cast)" : " (activate)");
            System.out.println("[LLM-AI] Chose: " + actionDesc);

            history.addExchange(turn, phase, "Chose " + actionDesc);

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

            // Gather opponent blockers (untapped creatures that could block)
            Map<String, List<Card>> opponentBlockers = new java.util.LinkedHashMap<>();
            for (Player opp : attacker.getOpponents()) {
                if (opp.hasLost()) continue;
                List<Card> blockers = new ArrayList<>();
                for (Card c : opp.getCreaturesInPlay()) {
                    if (!c.isTapped()) {
                        blockers.add(c);
                    }
                }
                opponentBlockers.put(opp.getName() + " (Life: " + opp.getLife() + ")", blockers);
            }

            // Build prompt
            String prompt = promptBuilder.buildAttackPrompt(gameState, potentialAttackers, opponentBlockers);

            // Query LLM
            LlmResponse response = llmClient.query(promptBuilder.getSystemPrompt(), prompt);
            stats.recordApiCall(response);

            // Parse response
            List<Integer> chosenIndices = responseParser.parseMultipleChoices(response.getText(), potentialAttackers.size());

            int turn = game.getPhaseHandler().getTurn();

            if (chosenIndices.isEmpty()) {
                System.out.println("[LLM-AI] Chose: No attackers");
                history.addExchange(turn, "COMBAT", "Chose no attackers");
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

            StringBuilder attackLog = new StringBuilder("Attacked with: ");
            for (int idx : chosenIndices) {
                Card attackingCreature = potentialAttackers.get(idx);
                if (CombatUtil.canAttack(attackingCreature, defender)) {
                    combat.addAttacker(attackingCreature, defender);
                    System.out.println("[LLM-AI] Attacking with: " + attackingCreature.getName());
                    attackLog.append(attackingCreature.getName()).append(", ");
                }
            }
            history.addExchange(turn, "COMBAT", attackLog.toString());

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

        // Get playable lands (filter out bounce lands that would loop with no other lands)
        CardCollection lands = ComputerUtilAbility.getAvailableLandsToPlay(game, player);
        if (lands != null) {
            int landsInPlay = player.getCardsIn(forge.game.zone.ZoneType.Battlefield).stream()
                .mapToInt(c -> c.isLand() ? 1 : 0).sum();
            for (Card land : lands) {
                // Skip bounce lands if player has 0-1 lands in play (would loop)
                String oracle = land.getOracleText() != null ? land.getOracleText().toLowerCase() : "";
                if (oracle.contains("return a land you control") && landsInPlay <= 1) {
                    continue;
                }
                for (SpellAbility sa : land.getAllPossibleAbilities(player, false)) {
                    if (sa instanceof LandAbility) {
                        actions.add(sa);
                        break;
                    }
                }
            }
        }

        // Get playable spells and abilities (exclude mana abilities and land activations)
        CardCollection available = ComputerUtilAbility.getAvailableCards(game, player);
        List<SpellAbility> spellAbilities = ComputerUtilAbility.getSpellAbilities(available, player);
        for (SpellAbility sa : spellAbilities) {
            if (!sa.canPlay()) continue;
            if (sa instanceof LandAbility) continue;
            // Skip mana abilities (tapping lands/rocks for mana)
            if (sa.getManaPart() != null) continue;
            if (sa.getApi() != null && sa.getApi() == forge.game.ability.ApiType.Mana) continue;
            // Skip activated abilities on lands already in play (tap abilities, etc.)
            Card host = sa.getHostCard();
            if (host != null && host.isLand() && host.isInPlay() && !sa.isSpell()) continue;
            // Verify mana can actually be paid (prevents "AI failed to play" errors)
            if (sa.isSpell() && !ComputerUtilMana.canPayManaCost(sa, player, 0, false)) continue;
            actions.add(sa);
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
