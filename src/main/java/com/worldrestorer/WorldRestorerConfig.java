package com.worldrestorer;

import net.minecraftforge.common.ForgeConfigSpec;

public class WorldRestorerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> ARCHIVE_PATH;
    public static final ForgeConfigSpec.ConfigValue<String> DIMENSION_ID;
    public static final ForgeConfigSpec.ConfigValue<String> EXTRACT_MODE;
    public static final ForgeConfigSpec.BooleanValue AUTO_EXTRACT_ON_START;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_RESTART_FOR_REEXTRACT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        ARCHIVE_PATH = builder
            .comment("Path to the zip archive containing dimension data.")
            .define("archivePath", "./worldrestorer/restore.zip");
        DIMENSION_ID = builder
            .comment("Dimension id for the restored world.")
            .define("dimensionId", "worldrestorer:restored_world");
        EXTRACT_MODE = builder
            .comment("Extract mode: REPLACE or MERGE.")
            .define("extractMode", "REPLACE");
        AUTO_EXTRACT_ON_START = builder
            .comment("Automatically extract on server start.")
            .define("autoExtractOnServerStart", true);
        REQUIRE_RESTART_FOR_REEXTRACT = builder
            .comment("If true, re-extracting while the dimension is loaded will require restart.")
            .define("requireRestartForReextract", true);
        builder.pop();

        SPEC = builder.build();
    }

    private WorldRestorerConfig() {
    }
}
