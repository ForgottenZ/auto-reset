package com.worldrestorer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class WorldRestorerService {
    private static final AtomicBoolean EXTRACTION_IN_PROGRESS = new AtomicBoolean(false);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STATE_FILE = "worldrestorer_state.json";
    private static final String PENDING_FILE = "worldrestorer.pending";
    private static WorldRestorerState cachedState;
    private static boolean stateDirty;

    public static CompletableFuture<ExtractionResult> scheduleExtract(MinecraftServer server, ExecutorService executor, boolean autoTriggered) {
        if (!EXTRACTION_IN_PROGRESS.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(new ExtractionResult(false, "Extraction already in progress", 0, Duration.ZERO));
        }
        return CompletableFuture.supplyAsync(() -> extract(server, autoTriggered), executor)
            .whenComplete((result, throwable) -> {
                EXTRACTION_IN_PROGRESS.set(false);
                if (throwable != null) {
                    WorldRestorerMod.LOGGER.error("Extraction failed", throwable);
                }
            });
    }

    public static boolean isExtractionInProgress() {
        return EXTRACTION_IN_PROGRESS.get();
    }

    public static void clearInProgress() {
        EXTRACTION_IN_PROGRESS.set(false);
    }

    public static ResourceLocation getDimensionId() {
        ResourceLocation parsed = ResourceLocation.tryParse(WorldRestorerConfig.DIMENSION_ID.get());
        return parsed != null ? parsed : new ResourceLocation(WorldRestorerMod.MODID, "restored_world");
    }

    public static Path resolveArchivePath(MinecraftServer server) {
        String configured = WorldRestorerConfig.ARCHIVE_PATH.get();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path gameDir = FMLPaths.GAMEDIR.get();
        return gameDir.resolve(path).normalize();
    }

    public static Path resolveDimensionPath(MinecraftServer server) {
        ResourceLocation dimensionId = getDimensionId();
        return server.getWorldPath(LevelResource.ROOT)
            .resolve("dimensions")
            .resolve(dimensionId.getNamespace())
            .resolve(dimensionId.getPath());
    }

    public static boolean isDimensionLoaded(MinecraftServer server) {
        ResourceLocation dimensionId = getDimensionId();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimensionId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDimensionReady(MinecraftServer server) {
        Path dimensionPath = resolveDimensionPath(server);
        if (!Files.exists(dimensionPath)) {
            return false;
        }
        return Files.exists(dimensionPath.resolve("region"));
    }

    public static void markPending(MinecraftServer server) {
        try {
            Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(PENDING_FILE), "pending");
        } catch (IOException e) {
            WorldRestorerMod.LOGGER.warn("Failed to mark pending extraction", e);
        }
    }

    public static boolean hasPending(MinecraftServer server) {
        return Files.exists(server.getWorldPath(LevelResource.ROOT).resolve(PENDING_FILE));
    }

    public static void clearPending(MinecraftServer server) {
        try {
            Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve(PENDING_FILE));
        } catch (IOException e) {
            WorldRestorerMod.LOGGER.warn("Failed to clear pending marker", e);
        }
    }

    public static void clearPlayerData(MinecraftServer server) {
        Path playerDataPath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.exists(playerDataPath)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerDataPath)) {
            int deleted = 0;
            for (Path child : stream) {
                deleteRecursively(child);
                deleted++;
            }
            if (deleted > 0) {
                WorldRestorerMod.LOGGER.info("Cleared {} playerdata entries at {}", deleted, playerDataPath);
            }
        } catch (IOException e) {
            WorldRestorerMod.LOGGER.warn("Failed to clear playerdata at {}", playerDataPath, e);
        }
    }

    public static WorldRestorerState loadState(MinecraftServer server) {
        if (cachedState != null) {
            return cachedState;
        }
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE);
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                cachedState = GSON.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8), WorldRestorerState.class);
            } catch (IOException e) {
                WorldRestorerMod.LOGGER.warn("Failed to read state file", e);
            }
        }
        if (cachedState == null) {
            cachedState = new WorldRestorerState();
        }
        return cachedState;
    }

    public static void saveStateIfDirty(MinecraftServer server) {
        if (!stateDirty || server == null) {
            return;
        }
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(cachedState), StandardCharsets.UTF_8);
            stateDirty = false;
        } catch (IOException e) {
            WorldRestorerMod.LOGGER.warn("Failed to write state file", e);
        }
    }

    private static ExtractionResult extract(MinecraftServer server, boolean autoTriggered) {
        Path archive = resolveArchivePath(server);
        if (archive == null) {
            return updateState(server, false, "Archive path is not configured", 0, Duration.ZERO);
        }
        if (!Files.exists(archive)) {
            return updateState(server, false, "Archive not found at " + archive, 0, Duration.ZERO);
        }

        if (autoTriggered && WorldRestorerConfig.REQUIRE_RESTART_FOR_REEXTRACT.get() && isDimensionLoaded(server)) {
            markPending(server);
            return updateState(server, false, "Dimension loaded; pending extraction on next restart", 0, Duration.ZERO);
        }

        Instant start = Instant.now();
        Path target = resolveDimensionPath(server);
        String extractMode = WorldRestorerConfig.EXTRACT_MODE.get().toUpperCase(Locale.ROOT);
        try {
            WorldRestorerMod.LOGGER.info("Starting extraction from {} to {} with mode {}", archive, target, extractMode);
            if ("REPLACE".equals(extractMode)) {
                replaceTarget(server, target);
            } else if (!"MERGE".equals(extractMode)) {
                return updateState(server, false, "Unknown extract mode: " + extractMode, 0, Duration.ZERO);
            }

            ExtractionSummary summary = unzipArchive(archive, target);
            clearPending(server);
            Duration duration = Duration.between(start, Instant.now());
            WorldRestorerMod.LOGGER.info("Extraction complete: {} files in {} ms", summary.filesExtracted, duration.toMillis());
            return updateState(server, true, "Extracted " + summary.filesExtracted + " files", summary.filesExtracted,
                duration);
        } catch (Exception e) {
            WorldRestorerMod.LOGGER.error("Extraction failed", e);
            return updateState(server, false, e.getMessage(), 0, Duration.between(start, Instant.now()));
        }
    }

    private static void replaceTarget(MinecraftServer server, Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        Path backupsRoot = server.getWorldPath(LevelResource.ROOT).resolve("worldrestorer_backups");
        Files.createDirectories(backupsRoot);
        String timestamp = String.valueOf(System.currentTimeMillis());
        Path backupDir = backupsRoot.resolve(target.getFileName() + "_" + timestamp);
        try {
            Files.move(target, backupDir, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            WorldRestorerMod.LOGGER.warn("Failed to move existing dimension to backup. Deleting instead.", e);
            deleteRecursively(target);
        }
    }

    private static ExtractionSummary unzipArchive(Path archive, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            String rootPrefix = findDimensionRoot(zipFile);
            if (rootPrefix == null) {
                throw new IOException("Zip must contain a dimension root with region/ directory.");
            }
            int fileCount = 0;
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(rootPrefix)) {
                    continue;
                }
                String relative = name.substring(rootPrefix.length());
                if (relative.isEmpty()) {
                    continue;
                }
                Path resolved = target.resolve(relative).normalize();
                if (!resolved.startsWith(target.normalize())) {
                    throw new IOException("Zip slip detected: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    try (InputStream input = new BufferedInputStream(zipFile.getInputStream(entry));
                         OutputStream output = new BufferedOutputStream(Files.newOutputStream(resolved))) {
                        input.transferTo(output);
                    }
                    fileCount++;
                }
            }
            return new ExtractionSummary(fileCount);
        }
    }

    private static String findDimensionRoot(ZipFile zipFile) throws IOException {
        boolean hasRegionAtRoot = false;
        List<String> topLevelDirs = new ArrayList<>();

        var entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.isEmpty()) {
                continue;
            }
            String[] parts = name.split("/");
            if (parts.length == 0) {
                continue;
            }
            if (parts.length > 0 && !parts[0].isBlank() && !topLevelDirs.contains(parts[0])) {
                topLevelDirs.add(parts[0]);
            }
            if (name.startsWith("region/")) {
                hasRegionAtRoot = true;
            }
        }
        if (hasRegionAtRoot) {
            return "";
        }
        if (topLevelDirs.size() == 1) {
            String rootCandidate = topLevelDirs.get(0) + "/";
            boolean foundRegion = zipFile.stream().anyMatch(entry -> entry.getName().startsWith(rootCandidate + "region/"));
            if (foundRegion) {
                return rootCandidate;
            }
        }
        return null;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static ExtractionResult updateState(MinecraftServer server, boolean success, String message, int files, Duration duration) {
        WorldRestorerState state = loadState(server);
        state.setLastExtractTime(Instant.now().toString());
        state.setLastExtractStatus(success ? "SUCCESS" : "FAILED");
        state.setLastExtractDetails(message);
        state.setLastExtractFiles(files);
        state.setLastExtractDurationMs(duration.toMillis());
        stateDirty = true;
        saveStateIfDirty(server);
        return new ExtractionResult(success, message, files, duration);
    }

    private record ExtractionSummary(int filesExtracted) {
    }

    public record ExtractionResult(boolean success, String message, int filesExtracted, Duration duration) {
    }
}
