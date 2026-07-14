package com.butchercraft.engine;

import com.butchercraft.engine.modifier.ModifierSystem;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quality.QualityGrade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductQualityTest {
    @Test
    void minimumAndMaximumScoresAreValid() {
        assertEquals(0, ProductQuality.ofScore(0).score());
        assertEquals(1000, ProductQuality.ofScore(1000).score());
    }

    @Test
    void gradeBoundariesAreDeterministic() {
        assertGrade(0, QualityGrade.POOR);
        assertGrade(199, QualityGrade.POOR);
        assertGrade(200, QualityGrade.FAIR);
        assertGrade(399, QualityGrade.FAIR);
        assertGrade(400, QualityGrade.GOOD);
        assertGrade(699, QualityGrade.GOOD);
        assertGrade(700, QualityGrade.EXCELLENT);
        assertGrade(899, QualityGrade.EXCELLENT);
        assertGrade(900, QualityGrade.PREMIUM);
        assertGrade(1000, QualityGrade.PREMIUM);
    }

    @Test
    void outOfRangeConstructionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProductQuality.ofScore(-1));
        assertThrows(IllegalArgumentException.class, () -> ProductQuality.ofScore(1001));
    }

    @Test
    void operationalAdjustmentsClampToValidRange() {
        assertEquals(ProductQuality.ofScore(0), ProductQuality.ofScore(20).adjustedByClamped(-100));
        assertEquals(ProductQuality.ofScore(1000), ProductQuality.ofScore(950).adjustedByClamped(100));
    }

    @Test
    void modifierApplicationToQualityIsDeterministic() {
        var modifiers = List.of(
                EngineTestFixtures.qualityModifier("small_bonus", 15, 20),
                EngineTestFixtures.qualityModifier("large_bonus", 35, 10)
        );

        int firstDelta = ModifierSystem.apply(modifiers).qualityDelta();
        int secondDelta = ModifierSystem.apply(modifiers).qualityDelta();

        assertEquals(50, firstDelta);
        assertEquals(firstDelta, secondDelta);
        assertEquals(ProductQuality.ofScore(700), ProductQuality.ofScore(650).adjustedByClamped(firstDelta));
    }

    private static void assertGrade(int score, QualityGrade grade) {
        assertEquals(grade, ProductQuality.ofScore(score).grade());
    }
}
