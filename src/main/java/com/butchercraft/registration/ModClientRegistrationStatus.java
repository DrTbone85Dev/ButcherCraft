package com.butchercraft.registration;

public final class ModClientRegistrationStatus {
    private static volatile boolean developmentWorkstationScreenRegistered;

    private ModClientRegistrationStatus() {
    }

    public static void markDevelopmentWorkstationScreenRegistered() {
        developmentWorkstationScreenRegistered = true;
    }

    public static boolean developmentWorkstationScreenRegistered() {
        return developmentWorkstationScreenRegistered;
    }
}
