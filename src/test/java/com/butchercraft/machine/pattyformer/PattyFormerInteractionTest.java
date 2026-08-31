package com.butchercraft.machine.pattyformer;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PattyFormerInteractionTest {
    @Test
    void normalUseOpensMenuAndSecondaryUseControlsPersistentRun() throws IOException {
        String source = source("src/main/java/com/butchercraft/machine/pattyformer/PattyFormerBlock.java");
        String emptyHand = source.substring(
                source.indexOf("protected InteractionResult useWithoutItem("),
                source.indexOf("protected ItemInteractionResult useItemOn(")
        );
        String heldItem = source.substring(
                source.indexOf("protected ItemInteractionResult useItemOn("),
                source.indexOf("protected void onRemove(")
        );

        assertTrue(emptyHand.contains("if (player.isSecondaryUseActive())"));
        assertTrue(emptyHand.contains("requestRunControl(level, pos, player)"));
        assertTrue(emptyHand.contains("openMenu(level, pos, player)"));
        assertTrue(heldItem.contains("if (player.isSecondaryUseActive())"));
        assertTrue(heldItem.contains("requestRunControl(level, pos, player)"));
        assertTrue(heldItem.contains("openMenu(level, pos, player)"));
        assertTrue(source.contains("player.openMenu(blockEntity, pos)"));
        assertTrue(source.contains("PattyFormerContinuousRunService.INSTANCE"));
        assertTrue(source.contains(".shiftControl(serverLevel, blockEntity)"));
        assertTrue(!source.contains("requestExplicitOperation"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
