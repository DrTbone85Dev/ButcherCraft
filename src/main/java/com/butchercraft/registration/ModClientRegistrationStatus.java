package com.butchercraft.registration;

public final class ModClientRegistrationStatus {
    private static volatile boolean developmentWorkstationScreenRegistered;
    private static volatile boolean grinderScreenRegistered;

    private ModClientRegistrationStatus() {
    }

    public static void markDevelopmentWorkstationScreenRegistered() {
        developmentWorkstationScreenRegistered = true;
    }

    public static boolean developmentWorkstationScreenRegistered() {
        return developmentWorkstationScreenRegistered;
    }

    public static void markGrinderScreenRegistered() {
        grinderScreenRegistered = true;
    }

    public static boolean grinderScreenRegistered() {
        return grinderScreenRegistered;
    }
}
