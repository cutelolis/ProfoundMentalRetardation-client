package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.RegistryListSetting;

public final class NoRenderModule extends Module {

    private final BoolSetting portalOverlay;
    private final BoolSetting spyglassOverlay;
    private final BoolSetting nausea;
    private final BoolSetting pumpkinOverlay;
    private final BoolSetting powderedSnowOverlay;
    private final BoolSetting fireOverlay;
    private final BoolSetting liquidOverlay;
    private final BoolSetting inWallOverlay;
    private final BoolSetting vignette;
    private final BoolSetting guiBackground;
    private final BoolSetting totemAnimation;
    private final BoolSetting eatingParticles;
    private final BoolSetting enchantGlint;

    private final BoolSetting bossBar;
    private final BoolSetting scoreboard;
    private final BoolSetting crosshair;
    private final BoolSetting title;
    private final BoolSetting heldItemName;
    private final BoolSetting obfuscation;
    private final BoolSetting potionIcons;

    private final BoolSetting weather;
    private final BoolSetting worldBorder;
    private final BoolSetting blindness;
    private final BoolSetting darkness;
    private final BoolSetting fog;
    private final BoolSetting enchantTableBook;
    private final BoolSetting signText;
    private final BoolSetting blockBreakOverlay;
    private final BoolSetting blockBreakParticles;
    private final BoolSetting beaconBeams;
    private final BoolSetting fallingBlocks;
    private final BoolSetting mapMarkers;
    private final BoolSetting mapContents;
    private final BoolSetting banners;
    private final BoolSetting fireworkExplosions;
    private final BoolSetting hideAllParticles;
    private final BoolSetting textureRotations;
    private final RegistryListSetting blockEntities;

    private final RegistryListSetting entities;
    private final BoolSetting dropSpawnPackets;
    private final BoolSetting armor;
    private final BoolSetting invisibility;
    private final BoolSetting glowing;
    private final BoolSetting spawnerEntities;
    private final BoolSetting deadEntities;
    private final BoolSetting nametags;

    private boolean lastTextureRotations;

    public NoRenderModule() {
        super("no-render", "No Render", ModuleCategory.RENDER,
            "Disables rendering of selected overlays, HUD elements, world features and entities.");

        portalOverlay = add(new BoolSetting("portal-overlay", "Portal Overlay", false)
            .group("Overlay").description("Hide nether portal overlay"));
        spyglassOverlay = add(new BoolSetting("spyglass-overlay", "Spyglass Overlay", false)
            .group("Overlay").description("Hide spyglass overlay"));
        nausea = add(new BoolSetting("nausea", "Nausea", false)
            .group("Overlay").description("Disable nausea/portal warp"));
        pumpkinOverlay = add(new BoolSetting("pumpkin-overlay", "Pumpkin Overlay", false)
            .group("Overlay").description("Hide pumpkin overlay"));
        powderedSnowOverlay = add(new BoolSetting("powdered-snow-overlay", "Powdered Snow Overlay", false)
            .group("Overlay").description("Hide powder snow overlay"));
        fireOverlay = add(new BoolSetting("fire-overlay", "Fire Overlay", false)
            .group("Overlay").description("Hide fire overlay"));
        liquidOverlay = add(new BoolSetting("liquid-overlay", "Liquid Overlay", false)
            .group("Overlay").description("Hide underwater overlay"));
        inWallOverlay = add(new BoolSetting("in-wall-overlay", "In-Wall Overlay", false)
            .group("Overlay").description("Hide in-block overlay"));
        vignette = add(new BoolSetting("vignette", "Vignette", false)
            .group("Overlay").description("Disable screen vignette"));
        guiBackground = add(new BoolSetting("gui-background", "GUI Background", false)
            .group("Overlay").description("Hide GUI background dim"));
        totemAnimation = add(new BoolSetting("totem-animation", "Totem Animation", false)
            .group("Overlay").description("Hide totem animation"));
        eatingParticles = add(new BoolSetting("eating-particles", "Eating Particles", false)
            .group("Overlay").description("Hide eating particles"));
        enchantGlint = add(new BoolSetting("enchantment-glint", "Enchantment Glint", false)
            .group("Overlay").description("Hide enchantment glint"));

        bossBar = add(new BoolSetting("boss-bar", "Boss Bar", false)
            .group("HUD").description("Hide boss bars"));
        scoreboard = add(new BoolSetting("scoreboard", "Scoreboard", false)
            .group("HUD").description("Hide scoreboard"));
        crosshair = add(new BoolSetting("crosshair", "Crosshair", false)
            .group("HUD").description("Hide crosshair"));
        title = add(new BoolSetting("title", "Title", false)
            .group("HUD").description("Hide on-screen titles"));
        heldItemName = add(new BoolSetting("held-item-name", "Held Item Name", false)
            .group("HUD").description("Hide held item name"));
        obfuscation = add(new BoolSetting("obfuscation", "Obfuscation", false)
            .group("HUD").description("Show obfuscated text"));
        potionIcons = add(new BoolSetting("potion-icons", "Potion Icons", false)
            .group("HUD").description("Hide effect icons"));

        weather = add(new BoolSetting("weather", "Weather", false)
            .group("World").description("Hide rain and snow"));
        worldBorder = add(new BoolSetting("world-border", "World Border", false)
            .group("World").description("Hide world border"));
        blindness = add(new BoolSetting("blindness", "Blindness", false)
            .group("World").description("Disable blindness fog"));
        darkness = add(new BoolSetting("darkness", "Darkness", false)
            .group("World").description("Disable darkness fog"));
        fog = add(new BoolSetting("fog", "Fog", false)
            .group("World").description("Disable fog"));
        enchantTableBook = add(new BoolSetting("enchantment-table-book", "Enchant Table Book", false)
            .group("World").description("Hide enchant table book"));
        signText = add(new BoolSetting("sign-text", "Sign Text", false)
            .group("World").description("Hide sign text"));
        blockBreakOverlay = add(new BoolSetting("block-break-overlay", "Block Break Overlay", false)
            .group("World").description("Hide block crack overlay"));
        blockBreakParticles = add(new BoolSetting("block-break-particles", "Block Break Particles", false)
            .group("World").description("Hide block break particles"));
        beaconBeams = add(new BoolSetting("beacon-beams", "Beacon Beams", false)
            .group("World").description("Hide beacon beams"));
        fallingBlocks = add(new BoolSetting("falling-blocks", "Falling Blocks", false)
            .group("World").description("Hide falling blocks"));
        mapMarkers = add(new BoolSetting("map-markers", "Map Markers", false)
            .group("World").description("Hide map markers"));
        mapContents = add(new BoolSetting("map-contents", "Map Contents", false)
            .group("World").description("Hide map contents"));
        banners = add(new BoolSetting("banners", "Banners", false)
            .group("World").description("Hide banners"));
        fireworkExplosions = add(new BoolSetting("firework-explosions", "Firework Explosions", false)
            .group("World").description("Hide firework explosions"));
        hideAllParticles = add(new BoolSetting("hide-all-particles", "Hide All Particles", false)
            .group("World").description("Hide all particles"));
        textureRotations = add(new BoolSetting("texture-rotations", "Texture Rotations", false)
            .group("World").description("Constant texture rotations"));
        blockEntities = add(RegistryListSetting.blocks("block-entities", "Block Entities")
            .group("World").description("Hide chosen block entities"));

        entities = add(RegistryListSetting.entityTypes("entities", "Entities")
            .group("Entity").description("Hide chosen entities"));
        dropSpawnPackets = add(new BoolSetting("drop-spawn-packets", "Drop Spawn Packets", false)
            .group("Entity").description("Drop listed spawn packets"));
        armor = add(new BoolSetting("armor", "Armor", false)
            .group("Entity").description("Hide entity armor"));
        invisibility = add(new BoolSetting("invisibility", "Invisibility", false)
            .group("Entity").description("Show invisible entities"));
        glowing = add(new BoolSetting("glowing", "Glowing", false)
            .group("Entity").description("Disable glowing outline"));
        spawnerEntities = add(new BoolSetting("spawner-entities", "Spawner Entities", false)
            .group("Entity").description("Hide spawner mobs"));
        deadEntities = add(new BoolSetting("dead-entities", "Dead Entities", false)
            .group("Entity").description("Hide dead entities"));
        nametags = add(new BoolSetting("nametags", "Nametags", false)
            .group("Entity").description("Hide entity nametags"));
    }

    @Override
    public void onEnable() {
        lastTextureRotations = textureRotations.get();
        push();
        rebuildChunks();
    }

    @Override
    public void onDisable() {
        NoRenderState.disable();
        rebuildChunks();
    }

    @Override
    public void tick() {
        push();

        boolean tr = textureRotations.get();
        if (tr != lastTextureRotations) {
            lastTextureRotations = tr;
            rebuildChunks();
        }
    }

    @Override
    public boolean ticksWhenDisabled() {
        return false;
    }

    @Override
    protected void onOptionValueChanged(String settingId) {

        if (isEnabled()) push();
    }

    private void push() {
        NoRenderState.portalOverlay = portalOverlay.get();
        NoRenderState.spyglassOverlay = spyglassOverlay.get();
        NoRenderState.nausea = nausea.get();
        NoRenderState.pumpkinOverlay = pumpkinOverlay.get();
        NoRenderState.powderedSnowOverlay = powderedSnowOverlay.get();
        NoRenderState.fireOverlay = fireOverlay.get();
        NoRenderState.liquidOverlay = liquidOverlay.get();
        NoRenderState.inWallOverlay = inWallOverlay.get();
        NoRenderState.vignette = vignette.get();
        NoRenderState.guiBackground = guiBackground.get();
        NoRenderState.totemAnimation = totemAnimation.get();
        NoRenderState.eatingParticles = eatingParticles.get();
        NoRenderState.enchantGlint = enchantGlint.get();

        NoRenderState.bossBar = bossBar.get();
        NoRenderState.scoreboard = scoreboard.get();
        NoRenderState.crosshair = crosshair.get();
        NoRenderState.title = title.get();
        NoRenderState.heldItemName = heldItemName.get();
        NoRenderState.obfuscation = obfuscation.get();
        NoRenderState.potionIcons = potionIcons.get();

        NoRenderState.weather = weather.get();
        NoRenderState.worldBorder = worldBorder.get();
        NoRenderState.blindness = blindness.get();
        NoRenderState.darkness = darkness.get();
        NoRenderState.fog = fog.get();
        NoRenderState.enchantTableBook = enchantTableBook.get();
        NoRenderState.signText = signText.get();
        NoRenderState.blockBreakOverlay = blockBreakOverlay.get();
        NoRenderState.blockBreakParticles = blockBreakParticles.get();
        NoRenderState.beaconBeams = beaconBeams.get();
        NoRenderState.fallingBlocks = fallingBlocks.get();
        NoRenderState.mapMarkers = mapMarkers.get();
        NoRenderState.mapContents = mapContents.get();
        NoRenderState.banners = banners.get();
        NoRenderState.fireworkExplosions = fireworkExplosions.get();
        NoRenderState.hideAllParticles = hideAllParticles.get();
        NoRenderState.textureRotations = textureRotations.get();
        NoRenderState.blockEntities = blockEntities.get();

        NoRenderState.entities = entities.get();
        NoRenderState.dropSpawnPackets = dropSpawnPackets.get();
        NoRenderState.armor = armor.get();
        NoRenderState.invisibility = invisibility.get();
        NoRenderState.glowing = glowing.get();
        NoRenderState.spawnerEntities = spawnerEntities.get();
        NoRenderState.deadEntities = deadEntities.get();
        NoRenderState.nametags = nametags.get();

        NoRenderState.enable();
    }

    private static void rebuildChunks() {

        try {
            ModuleRenderUtil.refreshWorldRenderer();
        } catch (Throwable ignored) {

        }
    }
}
