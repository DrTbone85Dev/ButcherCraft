package com.butchercraft.registration;

public final class ModClientRegistrationStatus {
    private static volatile boolean developmentWorkstationScreenRegistered;
    private static volatile boolean grinderScreenRegistered;
    private static volatile boolean bandsawScreenRegistered;
    private static volatile boolean packagingTableScreenRegistered;

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

    public static void markBandsawScreenRegistered() {
        bandsawScreenRegistered = true;
    }

    public static boolean bandsawScreenRegistered() {
        return bandsawScreenRegistered;
    }

    public static void markPackagingTableScreenRegistered() {
        packagingTableScreenRegistered = true;
    }

    public static boolean packagingTableScreenRegistered() {
        return packagingTableScreenRegistered;
    }
}
