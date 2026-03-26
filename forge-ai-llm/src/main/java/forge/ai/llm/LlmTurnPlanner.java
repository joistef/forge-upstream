package forge.ai.llm;

/**
 * Manages multi-turn strategic planning for the LLM AI.
 * Creates a plan every N turns and injects it into subsequent prompts.
 */
public class LlmTurnPlanner {

    private String currentPlan;
    private int planCreatedOnTurn;
    private int planHorizon;
    private int lifeAtPlanTime;
    private int creaturesAtPlanTime;

    public LlmTurnPlanner(int planHorizon) {
        this.planHorizon = planHorizon;
        this.planCreatedOnTurn = -1;
    }

    /**
     * Returns true if a new plan should be created.
     */
    public boolean needsNewPlan(int currentTurn, int currentLife, int currentCreatures) {
        // No plan yet
        if (currentPlan == null) return true;

        // Plan expired
        if (currentTurn >= planCreatedOnTurn + planHorizon) return true;

        // Dramatic change: life dropped significantly
        if (currentLife < lifeAtPlanTime - 10) return true;

        // Dramatic change: board wiped (lost most creatures)
        if (creaturesAtPlanTime > 2 && currentCreatures <= creaturesAtPlanTime / 2) return true;

        return false;
    }

    public void setPlan(String plan, int turn, int life, int creatures) {
        this.currentPlan = plan;
        this.planCreatedOnTurn = turn;
        this.lifeAtPlanTime = life;
        this.creaturesAtPlanTime = creatures;
        System.out.println("[LLM-AI] New plan (turns " + turn + "-" + (turn + planHorizon - 1) + "): " + plan);
    }

    public String getCurrentPlan() {
        return currentPlan;
    }

    public boolean hasPlan() {
        return currentPlan != null;
    }

    public void clear() {
        currentPlan = null;
        planCreatedOnTurn = -1;
    }
}
