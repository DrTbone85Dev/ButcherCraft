package com.butchercraft.world.planning;
public enum PlanningPriority {
    LOW(0), NORMAL(1), HIGH(2), URGENT(3), CRITICAL(4);
    private final int precedence;
    PlanningPriority(int precedence) { this.precedence = precedence; }
    public int precedence() { return precedence; }
}
