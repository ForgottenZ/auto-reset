# World Restorer (Minecraft 1.20.1 Forge)

This mod restores a full dimension (blocks + entities) from a local zip archive, and can also reset the entire world while moving players into a temporary holding dimension during the reset.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Java 17

## Configuration

Edit `config/worldrestorer-common.toml` after first launch:

- `archivePath`: Path to the zip archive (absolute or relative to the game directory).
- `dimensionId`: Dimension id (default `worldrestorer:restored_world`).
- `holdingDimensionId`: Temporary holding dimension used during world reset (default `worldrestorer:holding_world`).
- `extractMode`: `REPLACE` (default) or `MERGE`.
- `autoExtractOnServerStart`: Automatically extract on server start.
- `requireRestartForReextract`: If true, re-extracting while the dimension is loaded requires restart.

Example:

```toml
archivePath = "./worldrestorer/restore.zip"
dimensionId = "worldrestorer:restored_world"
holdingDimensionId = "worldrestorer:holding_world"
extractMode = "REPLACE"
autoExtractOnServerStart = true
requireRestartForReextract = true
```

## Zip Structure

The archive should contain the dimension root with at least `region/` and ideally `entities/`:

```
region/
  r.0.0.mca
entities/
  r.0.0.mca
poi/
  r.0.0.mca
```

The mod accepts either:

1. The above directories at the zip root, or
2. A single top-level folder that contains those directories.

If the zip does not contain a `region/` directory, extraction will fail.

## Commands

All commands are under `/worldrestore`:

- `/worldrestore status` — Show current config, zip detection, and last extraction result.
- `/worldrestore extract` — Trigger extraction (OP only).
- `/worldrestore reset` — Reset the world data (region/entities/poi/data + vanilla dimensions) while moving all players to the holding dimension, unload non-holding worlds, reload world data, then return them to the overworld spawn (OP only).
- `/worldrestore tp [player]` — Teleport to the restored dimension (OP only).

## How it works

- The mod registers a dimension (`worldrestorer:restored_world`) via data pack JSON.
- A temporary holding dimension (`worldrestorer:holding_world`) is also registered for the reset flow.
- On server start (or via command), it extracts the zip to:
  
  `<world>/dimensions/<namespace>/<path>/`

- Since the region and entities data are already in that folder, Minecraft loads blocks and entities normally when the dimension is accessed.

## Troubleshooting

- **Dimension is empty**: Ensure the zip contains `region/` at the correct level.
- **Entities are missing**: Make sure the zip includes `entities/`.
- **Need to re-extract**: If `requireRestartForReextract=true`, restart the server after running `/worldrestore extract`.
- **World reset did not change terrain**: Ensure no server plugins are rewriting region data and that the server has permission to delete files under the world folder.

## Build

```bash
./gradlew build
```

The built jar will be in `build/libs/`.
