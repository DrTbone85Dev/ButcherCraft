package com.butchercraft.world.planning;
public enum PlanningHorizon {
    IMMEDIATE(0, true), SHORT(1, true), MEDIUM(2, false), LONG(3, false);
    private final int precedence;
    private final boolean executable;
    PlanningHorizon(int precedence, boolean executable) {
        this.precedence = precedence;
        this.executable = executable;
    }
    public int precedence() { return precedence; }
    public boolean executable() { return executable; }
}
