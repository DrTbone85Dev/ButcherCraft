package com.butchercraft.world.workforce.department;

public final class DepartmentSchema {
    public static final int CURRENT_VERSION = 1;
    public static final String DIRECTORY_NAME = "butchercraft";
    public static final String FILE_NAME = "departments.json";

    public static final DepartmentId PROCESSING = new DepartmentId("processing");
    public static final DepartmentId PACKAGING = new DepartmentId("packaging");
    public static final DepartmentId SHIPPING = new DepartmentId("shipping");
    public static final DepartmentId OFFICE = new DepartmentId("office");
    public static final DepartmentId MAINTENANCE = new DepartmentId("maintenance");

    public static final int DEFAULT_PROCESSING_X = 100;
    public static final int DEFAULT_PROCESSING_Y = 64;
    public static final int DEFAULT_PROCESSING_Z = -20;
    public static final int DEFAULT_PROCESSING_RADIUS = 12;

    private DepartmentSchema() {
    }
}
