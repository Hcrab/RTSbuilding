package com.rtsbuilding.rtsbuilding.energy;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.RtsBuildEnergy;
import com.rtsbuilding.rtsbuilding.energy.server.RtsEnergyApiImpl;
import com.rtsbuilding.rtsbuilding.energy.server.RtsEnergyCostService;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsAPIImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point of the built-in addon mod {@code rtsbuilding_technologized} —
 * the energy &amp; power system (energy banks, thermal generators and the
 * per-player energy grid).
 * <p>
 * This mod is a separate project that gets packaged together with the main
 * {@code rtsbuilding} mod in the same JAR. It registers its own
 * blocks/items/capabilities under the {@code rtsbuilding_technologized}
 * namespace and shows up as a separate mod in the mods list.
 * <p>
 * The whole energy system can be switched off through the main mod's
 * {@code enableTechnologized} config option. The config file is read manually
 * here at construction time (NeoForge only loads configs after mod
 * construction), so when disabled nothing of this addon is registered or
 * loaded at all.
 */
@Mod(RtsEnergyMod.MODID)
public final class RtsEnergyMod {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Unique mod identifier of the built-in energy addon. */
    public static final String MODID = "rtsbuilding_technologized";

    /** Config file written by the main mod. */
    private static final String CONFIG_FILE = "rts_building/rtsbuilding-common.toml";
    /** Config key controlling this addon. */
    private static final String CONFIG_KEY = "enableTechnologized";

    public RtsEnergyMod(IEventBus modEventBus, ModContainer modContainer) {
        if (!isEnabledByConfig()) {
            LOGGER.info("rtsbuilding-technologized disabled by config — nothing will be registered");
            return;
        }
        RtsEnergyBlocks.register(modEventBus);
        RtsEnergyItems.register(modEventBus);
        RtsEnergyBlockEntities.register(modEventBus);
        RtsEnergyCapabilities.register(modEventBus);
        RtsEnergyCreativeTabs.register(modEventBus);
        RtsTerminalEnergyImpl.register(modEventBus);
        modEventBus.addListener(RtsEnergyMod::commonSetup);
    }

    /**
     * Reads the {@code enableTechnologized} flag straight from the main mod's
     * config file. NeoForge loads configs only after all mods are constructed,
     * so {@link Config#isTechnologizedEnabled()} isn't reliable here yet; parsing
     * the small TOML boolean manually gives us the user's value at construction.
     */
    private static boolean isEnabledByConfig() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
            if (Files.isRegularFile(configPath)) {
                for (String line : Files.readAllLines(configPath)) {
                    String s = line.trim();
                    if (s.startsWith(CONFIG_KEY + " =") || s.startsWith(CONFIG_KEY + "=")) {
                        String value = s.substring(s.indexOf('=') + 1).trim().toLowerCase();
                        return !value.startsWith("false");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read {} — defaulting to enabled", CONFIG_FILE, e);
        }
        return true;
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // Hook into the main mod's runtime services here — during construction the
        // main mod's RtsServer isn't initialized yet (that happens in its own
        // commonSetup), and touching RtsAPIImpl early would trip its static init.
        // The required-dependency order guarantees the main mod's commonSetup ran first.
        event.enqueueWork(() -> {
            if (!Config.isTechnologizedEnabled()) {
                LOGGER.info("rtsbuilding-technologized disabled by config — energy system inactive");
                return;
            }
            // Activate the build-operation energy cost only while this addon is loaded.
            RtsBuildEnergy.install(player -> RtsEnergyCostService.consume(player, 1));
            // Provide the RtsAPI.energy() implementation to the main mod.
            RtsAPIImpl.setEnergyApi(new RtsEnergyApiImpl());
            // Make the main mod's terminal energy-powered again.
            RtsTerminalEnergyImpl.installProvider();
        });
    }
}
