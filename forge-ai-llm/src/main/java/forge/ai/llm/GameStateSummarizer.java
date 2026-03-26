package forge.ai.llm;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.player.Player;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.zone.MagicStack;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GameStateSummarizer {

    private final int maxChars;

    public GameStateSummarizer(int maxChars) {
        this.maxChars = maxChars;
    }

    public String summarize(Game game, Player aiPlayer) {
        StringBuilder sb = new StringBuilder();

        // Game metadata
        sb.append("Turn: ").append(game.getPhaseHandler().getTurn());
        sb.append(" | Phase: ").append(game.getPhaseHandler().getPhase());
        sb.append(" | Active Player: ");
        sb.append(game.getPhaseHandler().getPlayerTurn() == aiPlayer ? "YOU" : game.getPhaseHandler().getPlayerTurn().getName());
        sb.append("\n\n");

        // AI player state (full detail)
        appendPlayerState(sb, aiPlayer, "YOU", true);

        // Opponent states
        for (Player opp : game.getRegisteredPlayers()) {
            if (opp != aiPlayer && !opp.hasLost()) {
                appendPlayerState(sb, opp, opp.getName(), false);
            }
        }

        // Stack
        MagicStack stack = game.getStack();
        if (!stack.isEmpty()) {
            sb.append("=== STACK ===\n");
            for (SpellAbilityStackInstance si : stack) {
                sb.append("  - ").append(si.getStackDescription()).append("\n");
            }
            sb.append("\n");
        }

        // Truncate if too long
        if (sb.length() > maxChars) {
            sb.setLength(maxChars - 20);
            sb.append("\n[...truncated]");
        }

        return sb.toString();
    }

    /**
     * Summarize the stack for reactive prompts.
     */
    public String summarizeStack(Game game) {
        MagicStack stack = game.getStack();
        if (stack.isEmpty()) return "(empty)";

        StringBuilder sb = new StringBuilder();
        for (SpellAbilityStackInstance si : stack) {
            sb.append("  - ").append(si.getStackDescription());
            if (si.getSpellAbility() != null && si.getSpellAbility().getActivatingPlayer() != null) {
                sb.append(" (by ").append(si.getSpellAbility().getActivatingPlayer().getName()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String summarizeForCombat(Game game, Player aiPlayer, Combat combat) {
        StringBuilder sb = new StringBuilder();
        sb.append("Turn: ").append(game.getPhaseHandler().getTurn());
        sb.append(" | Phase: ").append(game.getPhaseHandler().getPhase());
        sb.append("\n\n");

        // Abbreviated: just life totals and battlefield creatures
        for (Player p : game.getRegisteredPlayers()) {
            if (p.hasLost()) continue;
            String label = (p == aiPlayer) ? "YOU" : p.getName();
            sb.append("=== ").append(label).append(" (Life: ").append(p.getLife()).append(") ===\n");
            appendCreatures(sb, p);
            sb.append("\n");
        }

        return sb.toString();
    }

    private void appendPlayerState(StringBuilder sb, Player p, String label, boolean isAi) {
        sb.append("=== ").append(label).append(" (Life: ").append(p.getLife()).append(") ===\n");

        // Commander damage
        boolean hasCommanderDamage = false;
        StringBuilder cmdDmg = new StringBuilder();
        for (Map.Entry<Card, Integer> entry : p.getCommanderDamage()) {
            if (entry.getValue() > 0) {
                if (!hasCommanderDamage) {
                    cmdDmg.append("Commander Damage: ");
                    hasCommanderDamage = true;
                }
                cmdDmg.append(entry.getKey().getName()).append("=").append(entry.getValue()).append(" ");
            }
        }
        if (hasCommanderDamage) {
            sb.append(cmdDmg).append("\n");
        }

        // Poison counters
        if (p.getPoisonCounters() > 0) {
            sb.append("Poison: ").append(p.getPoisonCounters()).append("\n");
        }

        // Hand
        if (isAi) {
            CardCollectionView hand = p.getCardsIn(ZoneType.Hand);
            sb.append("Hand (").append(hand.size()).append("): ");
            for (Card c : hand) {
                sb.append(c.getName());
                if (c.getManaCost() != null) {
                    sb.append(" {").append(c.getManaCost().getShortString()).append("}");
                }
                sb.append(", ");
            }
            sb.append("\n");
        } else {
            sb.append("Hand: ").append(p.getCardsIn(ZoneType.Hand).size()).append(" cards\n");
        }

        // Battlefield by type
        appendBattlefield(sb, p);

        // Graveyard (names only, last 10)
        CardCollectionView grave = p.getCardsIn(ZoneType.Graveyard);
        if (!grave.isEmpty()) {
            sb.append("Graveyard (").append(grave.size()).append("): ");
            int count = 0;
            for (int i = grave.size() - 1; i >= 0 && count < 10; i--, count++) {
                sb.append(grave.get(i).getName()).append(", ");
            }
            if (grave.size() > 10) sb.append("...");
            sb.append("\n");
        }

        // Command zone
        CardCollectionView command = p.getCardsIn(ZoneType.Command);
        if (!command.isEmpty()) {
            sb.append("Command Zone: ");
            for (Card c : command) {
                sb.append(c.getName()).append(", ");
            }
            sb.append("\n");
        }

        // Library size
        sb.append("Library: ").append(p.getCardsIn(ZoneType.Library).size()).append(" cards\n");
        sb.append("\n");
    }

    private void appendBattlefield(StringBuilder sb, Player p) {
        CardCollectionView battlefield = p.getCardsIn(ZoneType.Battlefield);
        if (battlefield.isEmpty()) {
            sb.append("Battlefield: (empty)\n");
            return;
        }

        sb.append("Battlefield:\n");

        // Group by type
        List<Card> creatures = new ArrayList<>();
        List<Card> lands = new ArrayList<>();
        List<Card> artifacts = new ArrayList<>();
        List<Card> enchantments = new ArrayList<>();
        List<Card> planeswalkers = new ArrayList<>();
        List<Card> other = new ArrayList<>();

        for (Card c : battlefield) {
            if (c.isCreature()) creatures.add(c);
            else if (c.isLand()) lands.add(c);
            else if (c.isArtifact()) artifacts.add(c);
            else if (c.isEnchantment()) enchantments.add(c);
            else if (c.isPlaneswalker()) planeswalkers.add(c);
            else other.add(c);
        }

        if (!creatures.isEmpty()) {
            sb.append("  Creatures: ");
            appendCreatureList(sb, creatures);
        }

        if (!lands.isEmpty()) {
            sb.append("  Lands: ");
            appendLandList(sb, lands);
        }

        if (!artifacts.isEmpty()) {
            sb.append("  Artifacts: ");
            for (Card c : artifacts) {
                sb.append(c.getName());
                if (c.isTapped()) sb.append(" {Tapped}");
                sb.append(", ");
            }
            sb.append("\n");
        }

        if (!enchantments.isEmpty()) {
            sb.append("  Enchantments: ");
            for (Card c : enchantments) {
                sb.append(c.getName()).append(", ");
            }
            sb.append("\n");
        }

        if (!planeswalkers.isEmpty()) {
            sb.append("  Planeswalkers: ");
            for (Card c : planeswalkers) {
                sb.append(c.getName());
                int loyalty = c.getCounters(CounterEnumType.LOYALTY);
                if (loyalty > 0) sb.append(" (").append(loyalty).append(" loyalty)");
                sb.append(", ");
            }
            sb.append("\n");
        }
    }

    private void appendCreatures(StringBuilder sb, Player p) {
        List<Card> creatures = new ArrayList<>();
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isCreature()) creatures.add(c);
        }
        if (creatures.isEmpty()) {
            sb.append("  No creatures\n");
            return;
        }
        sb.append("  Creatures: ");
        appendCreatureList(sb, creatures);
    }

    private void appendCreatureList(StringBuilder sb, List<Card> creatures) {
        // Group identical tokens
        Map<String, Integer> tokenCounts = new LinkedHashMap<>();
        List<Card> nonTokens = new ArrayList<>();

        for (Card c : creatures) {
            if (c.isToken()) {
                String key = c.getName() + " (" + c.getNetPower() + "/" + c.getNetToughness() + ")";
                tokenCounts.merge(key, 1, Integer::sum);
            } else {
                nonTokens.add(c);
            }
        }

        for (Card c : nonTokens) {
            sb.append(c.getName());
            sb.append(" (").append(c.getNetPower()).append("/").append(c.getNetToughness()).append(")");
            if (c.isCommander()) sb.append(" [Commander]");
            if (c.isTapped()) sb.append(" {Tapped}");
            if (c.hasSickness()) sb.append(" {Sick}");

            // Key counters
            int p1 = c.getCounters(CounterEnumType.P1P1);
            if (p1 > 0) sb.append(" +").append(p1).append("/+").append(p1);

            // Key keywords
            if (c.hasKeyword("Flying")) sb.append(" Flying");
            if (c.hasKeyword("Deathtouch")) sb.append(" Deathtouch");
            if (c.hasKeyword("Indestructible")) sb.append(" Indestructible");
            if (c.hasKeyword("Hexproof")) sb.append(" Hexproof");

            sb.append(", ");
        }

        for (Map.Entry<String, Integer> entry : tokenCounts.entrySet()) {
            sb.append(entry.getKey()).append(" x").append(entry.getValue()).append(", ");
        }

        sb.append("\n");
    }

    private void appendLandList(StringBuilder sb, List<Card> lands) {
        // Group identical basic lands
        Map<String, int[]> basicCounts = new LinkedHashMap<>(); // name -> [total, untapped]
        List<Card> nonBasics = new ArrayList<>();

        for (Card c : lands) {
            if (c.isBasicLand()) {
                int[] counts = basicCounts.computeIfAbsent(c.getName(), k -> new int[]{0, 0});
                counts[0]++;
                if (!c.isTapped()) counts[1]++;
            } else {
                nonBasics.add(c);
            }
        }

        for (Map.Entry<String, int[]> entry : basicCounts.entrySet()) {
            sb.append(entry.getKey()).append(" x").append(entry.getValue()[0]);
            sb.append(" (").append(entry.getValue()[1]).append(" untapped)");
            sb.append(", ");
        }

        for (Card c : nonBasics) {
            sb.append(c.getName());
            if (c.isTapped()) sb.append(" {Tapped}");
            sb.append(", ");
        }

        sb.append("\n");
    }
}
