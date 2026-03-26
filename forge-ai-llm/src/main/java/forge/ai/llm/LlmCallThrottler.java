package forge.ai.llm;

import forge.game.Game;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

public class LlmCallThrottler {
    private int lastQueriedTurn = -1;
    private PhaseType lastQueriedPhase = null;
    private int callsThisTurn = 0;
    private int reactiveCallsThisTurn = 0;
    private final int maxCallsPerTurn;
    private final int maxReactiveCallsPerTurn;
    private boolean reactiveEnabled;

    public LlmCallThrottler(int maxCallsPerTurn) {
        this(maxCallsPerTurn, 3, true);
    }

    public LlmCallThrottler(int maxCallsPerTurn, int maxReactiveCallsPerTurn, boolean reactiveEnabled) {
        this.maxCallsPerTurn = maxCallsPerTurn;
        this.maxReactiveCallsPerTurn = maxReactiveCallsPerTurn;
        this.reactiveEnabled = reactiveEnabled;
    }

    /**
     * Returns true if we should skip the LLM call and fall back to regular AI.
     */
    public boolean shouldSkip(Game game, Player aiPlayer) {
        PhaseHandler ph = game.getPhaseHandler();
        PhaseType phase = ph.getPhase();
        int turn = ph.getTurn();

        // Reset counters on new turn
        if (turn != lastQueriedTurn) {
            lastQueriedTurn = turn;
            callsThisTurn = 0;
            reactiveCallsThisTurn = 0;
        }

        // Check for reactive scenario: stack non-empty, opponent's turn
        if (!game.getStack().isEmpty() && ph.getPlayerTurn() != aiPlayer) {
            return shouldSkipReactive(game, aiPlayer);
        }

        // Regular proactive decision: only during strategic phases on our turn
        if (ph.getPlayerTurn() == aiPlayer) {
            if (phase != PhaseType.MAIN1 && phase != PhaseType.MAIN2
                && phase != PhaseType.COMBAT_DECLARE_ATTACKERS) {
                return true;
            }
        } else {
            // Opponent's turn, empty stack: only blocks
            if (phase != PhaseType.COMBAT_DECLARE_BLOCKERS) {
                return true;
            }
        }

        // Avoid duplicate calls in same phase of same turn
        if (phase == lastQueriedPhase) {
            return true;
        }

        // Rate limit per turn
        if (callsThisTurn >= maxCallsPerTurn) {
            return true;
        }

        lastQueriedPhase = phase;
        callsThisTurn++;
        return false;
    }

    /**
     * Checks whether to skip a reactive (instant-speed response) LLM call.
     * Called when stack is non-empty during opponent's turn.
     */
    private boolean shouldSkipReactive(Game game, Player aiPlayer) {
        if (!reactiveEnabled) return true;

        // Don't respond to our own spells on the stack
        if (game.getStack().peekAbility() != null
                && game.getStack().peekAbility().getActivatingPlayer() == aiPlayer) {
            return true;
        }

        // Reactive call budget
        if (reactiveCallsThisTurn >= maxReactiveCallsPerTurn) {
            return true;
        }

        reactiveCallsThisTurn++;
        return false;
    }

    /**
     * Returns true if this is a reactive scenario (stack non-empty, not our spell).
     */
    public boolean isReactiveScenario(Game game, Player aiPlayer) {
        return !game.getStack().isEmpty()
            && game.getPhaseHandler().getPlayerTurn() != aiPlayer
            && game.getStack().peekAbility() != null
            && game.getStack().peekAbility().getActivatingPlayer() != aiPlayer;
    }

    public void reset() {
        lastQueriedTurn = -1;
        lastQueriedPhase = null;
        callsThisTurn = 0;
        reactiveCallsThisTurn = 0;
    }
}
