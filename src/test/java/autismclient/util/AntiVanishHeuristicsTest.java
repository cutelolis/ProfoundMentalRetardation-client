package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntiVanishHeuristicsTest {
    @Test
    void ignoresExplosionAndNaturalBlockChanges() {
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:air"));
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:dirt"));
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:farmland"));
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:wheat"));
        assertFalse(AntiVanishHeuristics.potentialInteractiveBlock("minecraft:grass_block"));
    }

    @Test
    void ignoresGrassDirtSpreadButNotRealPlacements() {

        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:dirt"));
        assertTrue(AntiVanishHeuristics.naturalBlockNoise("minecraft:grass_block"));

        assertFalse(AntiVanishHeuristics.naturalBlockNoise("minecraft:stone"));
        assertFalse(AntiVanishHeuristics.naturalBlockNoise("minecraft:cobblestone"));
        assertFalse(AntiVanishHeuristics.naturalBlockNoise("minecraft:coarse_dirt"));
        assertFalse(AntiVanishHeuristics.naturalBlockNoise("minecraft:dirt_path"));
    }

    @Test
    void keepsActualInteractionBlocks() {
        assertTrue(AntiVanishHeuristics.blockEventInteraction("minecraft:chest"));
        assertTrue(AntiVanishHeuristics.blockEventInteraction("minecraft:shulker_box"));
        assertTrue(AntiVanishHeuristics.blockStateInteraction("minecraft:oak_door"));
        assertTrue(AntiVanishHeuristics.blockStateInteraction("minecraft:lever"));
    }

    @Test
    void ignoresFootstepsMobAndAmbientSounds() {

        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:block.stone.step"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:block.grass.step"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:entity.cow.step"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:entity.cow.ambient"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:entity.zombie.ambient"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:entity.generic.explode"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:block.crop.break"));
        assertFalse(AntiVanishHeuristics.suspiciousSound("minecraft:weather.rain"));
    }

    @Test
    void flagsOnlyPlayerInteractionSounds() {
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.chest.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.chest.close"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.barrel.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.ender_chest.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.shulker_box.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.wooden_door.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.wooden_trapdoor.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.fence_gate.open"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.stone_button.click_on"));
        assertTrue(AntiVanishHeuristics.suspiciousSound("minecraft:block.lever.click"));
    }

    @Test
    void excludesGenericDeathParticles() {
        assertFalse(AntiVanishHeuristics.suspiciousParticle("minecraft:poof"));
        assertTrue(AntiVanishHeuristics.suspiciousParticle("minecraft:crit"));
        assertTrue(AntiVanishHeuristics.suspiciousParticle("minecraft:smoke"));
    }
}
