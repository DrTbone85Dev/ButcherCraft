package com.butchercraft.client.screen;

import com.butchercraft.ButcherCraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Asset-facing layout contract for the Packaging Table screen.
 */
public final class PackagingTableGuiLayout {
    public static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "textures/gui/packaging_table.png");
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 256;
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 166;
    public static final int PROGRESS_X = 116;
    public static final int PROGRESS_Y = 39;
    public static final int PROGRESS_WIDTH = 20;
    public static final int PROGRESS_HEIGHT = 6;
    public static final int STATUS_X = 108;
    public static final int STATUS_Y = 52;
    public static final int STATUS_WIDTH = 60;
    public static final int MEAT_LABEL_CENTER_X = 40;
    public static final int SUPPLY_TRAY_LABEL_CENTER_X = 70;
    public static final int SUPPLY_WRAP_LABEL_CENTER_X = 100;
    public static final int INPUT_LABEL_Y = 17;
    public static final int RESULT_LABEL_CENTER_X = 70;
    public static final int RESULT_LABEL_Y = 75;

    private PackagingTableGuiLayout() {
    }
}
