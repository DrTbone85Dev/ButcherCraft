package com.butchercraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ButcherCraftIdentityTest {
    @Test
    void approvedIdentityConstantsAreStable() {
        assertEquals("ButcherCraft", ButcherCraft.PROJECT_NAME);
        assertEquals("butchercraft", ButcherCraft.MOD_ID);
    }
}
