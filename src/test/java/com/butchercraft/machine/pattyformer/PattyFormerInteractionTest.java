package com.butchercraft.machine.pattyformer;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PattyFormerInteractionTest {
    @Test
    void normalUseOpensMenuAndSecondaryUseRequestsOneOperation() throws IOException {
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
        assertTrue(emptyHand.contains("requestExplicitOperation(level, pos)"));
        assertTrue(emptyHand.contains("openMenu(level, pos, player)"));
        assertTrue(heldItem.contains("if (player.isSecondaryUseActive())"));
        assertTrue(heldItem.contains("requestExplicitOperation(level, pos)"));
        assertTrue(heldItem.contains("openMenu(level, pos, player)"));
        assertTrue(source.contains("player.openMenu(blockEntity, pos)"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }
}
