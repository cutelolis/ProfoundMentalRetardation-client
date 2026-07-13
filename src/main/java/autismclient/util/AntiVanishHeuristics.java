package autismclient.util;

import java.util.Locale;

public final class AntiVanishHeuristics {
    private AntiVanishHeuristics() {
    }

    public static boolean suspiciousSound(String id) {
        String path = path(id);

        return path.contains("chest.open") || path.contains("chest.close") || path.contains("chest.locked")
            || path.contains("barrel.open") || path.contains("barrel.close")
            || path.contains("shulker_box.open") || path.contains("shulker_box.close")
            || path.contains("door.open") || path.contains("door.close")
            || path.contains("fence_gate.open") || path.contains("fence_gate.close")
            || path.contains("button.click") || path.contains("lever.click");
    }

    public static boolean suspiciousParticle(String id) {
        String path = path(id);
        return path.contains("crit") || path.contains("enchanted_hit") || path.contains("damage_indicator")
            || path.contains("smoke") || path.equals("block");
    }

    public static boolean blockEventInteraction(String id) {
        String path = path(id);
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
    }

    public static boolean blockStateInteraction(String id) {
        String path = path(id);
        return path.contains("door") || path.contains("trapdoor") || path.contains("button")
            || path.contains("lever") || path.contains("barrel");
    }

    public static boolean potentialInteractiveBlock(String id) {
        return blockEventInteraction(id) || blockStateInteraction(id);
    }

    public static boolean isAir(String id) {
        String path = path(id);
        return path.equals("air") || path.equals("cave_air") || path.equals("void_air");
    }

    public static boolean naturalBlockNoise(String id) {
        String path = path(id);
        return path.equals("dirt")
            || path.contains("water") || path.contains("lava") || path.contains("bubble")
            || path.contains("fire") || path.contains("leaves") || path.contains("snow")
            || path.contains("ice") || path.contains("grass") || path.contains("fern")
            || path.contains("seagrass") || path.contains("kelp") || path.contains("vine")
            || path.contains("coral") || path.contains("sculk") || path.contains("redstone")
            || path.contains("piston") || path.contains("sapling") || path.contains("mushroom")
            || path.contains("flower") || path.contains("wheat") || path.contains("carrot")
            || path.contains("potato") || path.contains("beetroot") || path.contains("stem")
            || path.contains("cane") || path.contains("cactus") || path.contains("bamboo")
            || path.contains("chorus") || path.contains("berr") || path.contains("moss")
            || path.contains("azalea") || path.contains("dripleaf") || path.contains("dripstone")
            || path.contains("torch") || path.contains("candle") || path.contains("repeater")
            || path.contains("comparator") || path.contains("observer") || path.contains("nether_wart")
            || path.contains("cocoa") || path.contains("spore") || path.contains("lichen");
    }

    public static String path(String id) {
        if (id == null) return "unknown";
        int split = id.indexOf(':');
        return (split >= 0 && split + 1 < id.length() ? id.substring(split + 1) : id).toLowerCase(Locale.ROOT);
    }
}
