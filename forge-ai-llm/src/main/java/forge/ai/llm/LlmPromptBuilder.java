package forge.ai.llm;

import forge.game.card.Card;
import forge.game.spellability.SpellAbility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class LlmPromptBuilder {

    private final String systemPrompt;

    public LlmPromptBuilder() {
        this.systemPrompt = loadSystemPrompt();
    }

    private String loadSystemPrompt() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("llm-system-prompt.txt")) {
            if (is == null) {
                return getDefaultSystemPrompt();
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return getDefaultSystemPrompt();
        }
    }

    private String getDefaultSystemPrompt() {
        return "You are an expert Magic: The Gathering Commander player. "
             + "Respond with ONLY a number to select an action, or PASS. "
             + "For attacks, respond with comma-separated numbers or NONE. "
             + "For blocks, respond with blocker->attacker pairs or NONE.";
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String buildPlayPrompt(String gameState, List<SpellAbility> legalActions) {
        StringBuilder sb = new StringBuilder();
        sb.append("GAME STATE:\n");
        sb.append(gameState);
        sb.append("\nLEGAL ACTIONS:\n");

        for (int i = 0; i < legalActions.size(); i++) {
            SpellAbility sa = legalActions.get(i);
            sb.append(i).append(": ").append(describeAction(sa)).append("\n");
        }

        sb.append("PASS: Do nothing and pass priority\n");
        sb.append("\nChoose action:");
        return sb.toString();
    }

    public String buildAttackPrompt(String gameState, List<Card> potentialAttackers) {
        StringBuilder sb = new StringBuilder();
        sb.append("COMBAT - DECLARE ATTACKERS\n\n");
        sb.append(gameState);
        sb.append("\nYOUR CREATURES THAT CAN ATTACK:\n");

        for (int i = 0; i < potentialAttackers.size(); i++) {
            Card c = potentialAttackers.get(i);
            sb.append(i).append(": ").append(describeCreature(c)).append("\n");
        }

        sb.append("\nSelect which creatures to attack with (comma-separated numbers), or NONE:");
        return sb.toString();
    }

    public String buildBlockPrompt(String gameState, List<Card> potentialBlockers, List<Card> attackers) {
        StringBuilder sb = new StringBuilder();
        sb.append("COMBAT - DECLARE BLOCKERS\n\n");
        sb.append(gameState);

        sb.append("\nATTACKING CREATURES:\n");
        for (int i = 0; i < attackers.size(); i++) {
            Card c = attackers.get(i);
            sb.append("A").append(i).append(": ").append(describeCreature(c))
              .append(" (controlled by ").append(c.getController().getName()).append(")\n");
        }

        sb.append("\nYOUR POTENTIAL BLOCKERS:\n");
        for (int i = 0; i < potentialBlockers.size(); i++) {
            Card c = potentialBlockers.get(i);
            sb.append("B").append(i).append(": ").append(describeCreature(c)).append("\n");
        }

        sb.append("\nAssign blockers as blocker_num->attacker_num pairs (e.g. 0->1,2->0), or NONE:");
        return sb.toString();
    }

    private String describeAction(SpellAbility sa) {
        StringBuilder sb = new StringBuilder();
        Card host = sa.getHostCard();

        if (sa.isSpell()) {
            sb.append("Cast ").append(host.getName());
        } else if (sa.isLandAbility()) {
            sb.append("Play ").append(host.getName()).append(" (Land)");
        } else {
            sb.append("Activate ").append(host.getName());
        }

        // Mana cost
        if (sa.getPayCosts() != null && sa.getPayCosts().getTotalMana() != null
                && !sa.getPayCosts().getTotalMana().isNoCost()) {
            sb.append(" (Cost: {").append(sa.getPayCosts().getTotalMana().getShortString()).append("})");
        }

        // Brief description
        String desc = sa.getStackDescription();
        if (desc != null && !desc.isEmpty() && desc.length() < 120) {
            sb.append(" -- ").append(desc);
        } else {
            // Fallback to shorter description
            desc = sa.getDescription();
            if (desc != null && !desc.isEmpty()) {
                sb.append(" -- ").append(desc.substring(0, Math.min(100, desc.length())));
            }
        }

        // Card type hint for creatures
        if (host.isCreature() && sa.isSpell()) {
            sb.append(" [").append(host.getNetPower()).append("/").append(host.getNetToughness()).append("]");
        }

        return sb.toString();
    }

    private String describeCreature(Card c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getName());
        sb.append(" (").append(c.getNetPower()).append("/").append(c.getNetToughness()).append(")");

        if (c.hasKeyword("Flying")) sb.append(" Flying");
        if (c.hasKeyword("Deathtouch")) sb.append(" Deathtouch");
        if (c.hasKeyword("First Strike")) sb.append(" First Strike");
        if (c.hasKeyword("Double Strike")) sb.append(" Double Strike");
        if (c.hasKeyword("Trample")) sb.append(" Trample");
        if (c.hasKeyword("Lifelink")) sb.append(" Lifelink");
        if (c.hasKeyword("Indestructible")) sb.append(" Indestructible");
        if (c.hasKeyword("Vigilance")) sb.append(" Vigilance");
        if (c.hasKeyword("Menace")) sb.append(" Menace");

        return sb.toString();
    }
}
