package forge.ai.llm;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes a player's deck at game start to generate a brief for the LLM system prompt.
 * Checks for user-provided deck profiles first, falls back to auto-analysis.
 */
public class DeckAnalyzer {

    /**
     * Generate a deck brief for the LLM. Checks for user override first.
     */
    public static String generateBrief(Player player, String profileDir) {
        // Check for user-provided deck profile
        String deckName = getDeckName(player);
        if (profileDir != null && !profileDir.isEmpty()) {
            Path profilePath = Paths.get(
                profileDir.replace("~", System.getProperty("user.home")),
                sanitizeFilename(deckName) + ".txt");
            if (Files.exists(profilePath)) {
                try {
                    String userProfile = Files.readString(profilePath);
                    System.out.println("[LLM-AI] Loaded deck profile from: " + profilePath);
                    return userProfile;
                } catch (IOException e) {
                    System.err.println("[LLM-AI] Failed to read deck profile: " + e.getMessage());
                }
            }
        }

        // Auto-analyze
        return autoAnalyze(player);
    }

    private static String autoAnalyze(Player player) {
        String deckName = getDeckName(player);

        // Get commander info
        List<String> commanderNames = new ArrayList<>();
        try {
            for (Card c : player.getCommanders()) {
                commanderNames.add(c.getName());
            }
        } catch (Exception e) {
            // Commander lookup can fail on some deck configurations
        }

        // Count cards by type from library + hand + battlefield + graveyard + command
        int creatures = 0, instants = 0, sorceries = 0, artifacts = 0;
        int enchantments = 0, lands = 0, planeswalkers = 0;
        double totalCmc = 0;
        int nonLandCount = 0;
        Map<String, Integer> themes = new LinkedHashMap<>();

        List<Card> allCards = new ArrayList<>();
        allCards.addAll(player.getCardsIn(ZoneType.Library));
        allCards.addAll(player.getCardsIn(ZoneType.Hand));
        allCards.addAll(player.getCardsIn(ZoneType.Battlefield));
        allCards.addAll(player.getCardsIn(ZoneType.Graveyard));
        allCards.addAll(player.getCardsIn(ZoneType.Command));

        for (Card c : allCards) {
            try {
                if (c.isCreature()) creatures++;
                else if (c.isInstant()) instants++;
                else if (c.isSorcery()) sorceries++;
                else if (c.isArtifact()) artifacts++;
                else if (c.isEnchantment()) enchantments++;
                else if (c.isPlaneswalker()) planeswalkers++;

                if (c.isLand()) {
                    lands++;
                } else {
                    totalCmc += c.getCMC();
                    nonLandCount++;
                }
            } catch (Exception e) { continue; }

            // Theme detection from oracle text
            String oracle = "";
            try { oracle = c.getOracleText() != null ? c.getOracleText().toLowerCase() : ""; }
            catch (Exception e) { /* skip theme detection for this card */ }
            if (oracle.contains("sacrifice")) themes.merge("sacrifice", 1, Integer::sum);
            if (oracle.contains("graveyard")) themes.merge("recursion", 1, Integer::sum);
            if (oracle.contains("create") && oracle.contains("token")) themes.merge("tokens", 1, Integer::sum);
            if (oracle.contains("counter target")) themes.merge("counterspells", 1, Integer::sum);
            if (oracle.contains("draw") && oracle.contains("card")) themes.merge("card draw", 1, Integer::sum);
            if (oracle.contains("each opponent") && (oracle.contains("loses") || oracle.contains("damage")))
                themes.merge("drain", 1, Integer::sum);
        }

        double avgCmc = nonLandCount > 0 ? totalCmc / nonLandCount : 0;

        // Build brief
        StringBuilder sb = new StringBuilder();
        sb.append("You are playing '").append(deckName).append("'");

        if (!commanderNames.isEmpty()) {
            sb.append(". Commander: ").append(String.join(" & ", commanderNames));
        }

        sb.append(". Composition: ").append(creatures).append(" creatures");
        sb.append(", ").append(instants + sorceries).append(" spells");
        sb.append(", ").append(artifacts).append(" artifacts");
        sb.append(", ").append(enchantments).append(" enchantments");
        sb.append(", ").append(lands).append(" lands");
        sb.append(". Avg CMC: ").append(String.format("%.1f", avgCmc));

        // Top themes (those with 3+ cards)
        List<String> topThemes = new ArrayList<>();
        themes.entrySet().stream()
            .filter(e -> e.getValue() >= 3)
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(4)
            .forEach(e -> topThemes.add(e.getKey()));

        if (!topThemes.isEmpty()) {
            sb.append(". Themes: ").append(String.join(", ", topThemes));
        }

        sb.append(".");
        return sb.toString();
    }

    private static String getDeckName(Player player) {
        RegisteredPlayer rp = player.getRegisteredPlayer();
        if (rp != null && rp.getDeck() != null && rp.getDeck().getName() != null) {
            return rp.getDeck().getName();
        }
        return "Unknown Deck";
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim();
    }
}
