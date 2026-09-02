package com.butchercraft.workstation.reservation;

public final class WorkstationReservationSchema {
    public static final int LEGACY_VERSION = 1;
    public static final int CURRENT_VERSION = 2;
    public static final String DIRECTORY_NAME = "butchercraft";
    public static final String FILE_NAME = "workstation_reservations.json";
    public static final String CONFIGURATION_IDENTITY =
            "butchercraft:workstation_reservation_configuration/v2";

    private WorkstationReservationSchema() {
    }
}
