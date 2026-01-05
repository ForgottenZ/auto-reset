package com.worldrestorer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.EntityArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import java.nio.file.Path;

public class WorldRestorerCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worldrestore")
            .then(Commands.literal("status")
                .executes(context -> showStatus(context.getSource())))
            .then(Commands.literal("extract")
                .requires(source -> source.hasPermission(2))
                .executes(context -> extract(context.getSource())))
            .then(Commands.literal("tp")
                .requires(source -> source.hasPermission(2))
                .executes(context -> teleport(context.getSource(), null))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> teleport(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }

    private static int showStatus(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Path archive = WorldRestorerService.resolveArchivePath(server);
        Path dimensionPath = WorldRestorerService.resolveDimensionPath(server);
        WorldRestorerState state = WorldRestorerService.loadState(server);
        boolean archiveExists = archive != null && archive.toFile().exists();

        source.sendSuccess(() -> Component.literal("WorldRestorer status:").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Archive: " + archive).withStyle(archiveExists ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.literal("Dimension: " + WorldRestorerService.getDimensionId()), false);
        source.sendSuccess(() -> Component.literal("Target directory: " + dimensionPath), false);
        if (state.getLastExtractTime() != null) {
            source.sendSuccess(() -> Component.literal("Last extract: " + state.getLastExtractTime() + " (" + state.getLastExtractStatus() + ")"), false);
            source.sendSuccess(() -> Component.literal("Details: " + state.getLastExtractDetails()), false);
            source.sendSuccess(() -> Component.literal("Files: " + state.getLastExtractFiles() + ", Duration: " + state.getLastExtractDurationMs() + " ms"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Last extract: none"), false);
        }
        return 1;
    }

    private static int extract(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (WorldRestorerService.isExtractionInProgress()) {
            source.sendFailure(Component.literal("Extraction already in progress."));
            return 0;
        }
        if (WorldRestorerConfig.REQUIRE_RESTART_FOR_REEXTRACT.get() && WorldRestorerService.isDimensionLoaded(server)) {
            WorldRestorerService.markPending(server);
            source.sendSuccess(() -> Component.literal("Dimension already loaded. Marked for extraction on next restart."), true);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Starting extraction..."), true);
        WorldRestorerService.scheduleExtract(server, WorldRestorerMod.EXTRACT_EXECUTOR, false)
            .thenAccept(result -> server.execute(() -> {
                if (result.success()) {
                    source.sendSuccess(() -> Component.literal("Extraction completed: " + result.message()).withStyle(ChatFormatting.GREEN), true);
                } else {
                    source.sendFailure(Component.literal("Extraction failed: " + result.message()));
                }
            }));
        return 1;
    }

    private static int teleport(CommandSourceStack source, ServerPlayer target) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = target == null ? source.getPlayer() : target;
        if (player == null) {
            source.sendFailure(Component.literal("Player not found."));
            return 0;
        }
        if (!WorldRestorerService.isDimensionReady(server)) {
            source.sendFailure(Component.literal("Restored dimension is not ready. Ensure extraction succeeded."));
            return 0;
        }
        ResourceLocation dimensionId = WorldRestorerService.getDimensionId();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            source.sendFailure(Component.literal("Restored dimension is not available."));
            return 0;
        }

        BlockPos targetPos = new BlockPos(0, 80, 0);
        if (level.isEmptyBlock(targetPos.below())) {
            level.setBlockAndUpdate(targetPos.below(), Blocks.BEDROCK.defaultBlockState());
        }
        if (!level.isEmptyBlock(targetPos)) {
            level.setBlockAndUpdate(targetPos, Blocks.AIR.defaultBlockState());
        }

        player.teleportTo(level, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
        source.sendSuccess(() -> Component.literal("Teleported to restored world."), true);
        return 1;
    }
}
