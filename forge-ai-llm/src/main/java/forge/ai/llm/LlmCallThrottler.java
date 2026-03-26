package forge.ai.llm;

import forge.game.Game;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

public class LlmCallThrottler {
    private int lastQueriedTurn = -1;
    private PhaseType lastQueriedPhase = null;
    private int callsThisTurn = 0;
    private int maxCallsPerTurn;

    public LlmCallThrottler(int maxCallsPerTurn) {
        this.maxCallsPerTurn = maxCallsPerTurn;
    }

    /**
     * Returns true if we should skip the LLM call and fall back to regular AI.
     */
    public boolean shouldSkip(Game game, Player aiPlayer) {
        PhaseHandler ph = game.getPhaseHandler();
        PhaseType phase = ph.getPhase();
        int turn = ph.getTurn();

        // Only query LLM during strategic phases
        if (ph.getPlayerTurn() == aiPlayer) {
            // Our turn: only Main1, Main2, Declare Attackers
            if (phase != PhaseType.MAIN1 && phase != PhaseType.MAIN2
                && phase != PhaseType.COMBAT_DECLARE_ATTACKERS) {
                return true;
            }
        } else {
            // Opponent's turn: only during their combat (for blocks)
            if (phase != PhaseType.COMBAT_DECLARE_BLOCKERS) {
                return true;
            }
        }

        // Avoid duplicate calls in same phase of same turn
        if (turn == lastQueriedTurn && phase == lastQueriedPhase) {
            return true;
        }

        // Rate limit per turn
        if (turn == lastQueriedTurn && callsThisTurn >= maxCallsPerTurn) {
            return true;
        }

        // Update tracking
        if (turn != lastQueriedTurn) {
            lastQueriedTurn = turn;
            callsThisTurn = 0;
        }
        lastQueriedPhase = phase;
        callsThisTurn++;

        return false;
    }

    public void reset() {
        lastQueriedTurn = -1;
        lastQueriedPhase = null;
        callsThisTurn = 0;
    }
}
