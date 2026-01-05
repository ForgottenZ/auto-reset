package com.worldrestorer;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod(WorldRestorerMod.MODID)
public class WorldRestorerMod {
    public static final String MODID = "worldrestorer";
    public static final Logger LOGGER = LogUtils.getLogger();
    static final ExecutorService EXTRACT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WorldRestorer-Extractor");
        thread.setDaemon(true);
        return thread;
    });

    public WorldRestorerMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WorldRestorerConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        WorldRestorerCommands.register(event.getDispatcher());
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (WorldRestorerConfig.AUTO_EXTRACT_ON_START.get()) {
            WorldRestorerService.scheduleExtract(event.getServer(), EXTRACT_EXECUTOR, true);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        EXTRACT_EXECUTOR.shutdown();
        WorldRestorerService.clearInProgress();
        WorldRestorerService.saveStateIfDirty(ServerLifecycleHooks.getCurrentServer());
    }
}
