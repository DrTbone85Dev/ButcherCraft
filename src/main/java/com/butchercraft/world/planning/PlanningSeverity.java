package com.butchercraft.world.planning;
public enum PlanningSeverity {
    WARNING(0), BLOCKING(1), CRITICAL(2), FATAL(3);
    private final int precedence;
    PlanningSeverity(int precedence) { this.precedence = precedence; }
    public int precedence() { return precedence; }
}
