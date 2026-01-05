# World Restorer (Minecraft 1.20.1 Forge)

This mod restores a full dimension (blocks + entities) from a local zip archive. The zip is unpacked into a dedicated dimension directory so that region + entities data is loaded by vanilla storage.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Java 17

## Configuration

Edit `config/worldrestorer-common.toml` after first launch:

- `archivePath`: Path to the zip archive (absolute or relative to the game directory).
- `dimensionId`: Dimension id (default `worldrestorer:restored_world`).
- `extractMode`: `REPLACE` (default) or `MERGE`.
- `autoExtractOnServerStart`: Automatically extract on server start.
- `requireRestartForReextract`: If true, re-extracting while the dimension is loaded requires restart.

Example:

```toml
archivePath = "./worldrestorer/restore.zip"
dimensionId = "worldrestorer:restored_world"
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
- `/worldrestore tp [player]` — Teleport to the restored dimension (OP only).

## How it works

- The mod registers a dimension (`worldrestorer:restored_world`) via data pack JSON.
- On server start (or via command), it extracts the zip to:
  
  `<world>/dimensions/<namespace>/<path>/`

- Since the region and entities data are already in that folder, Minecraft loads blocks and entities normally when the dimension is accessed.

## Troubleshooting

- **Dimension is empty**: Ensure the zip contains `region/` at the correct level.
- **Entities are missing**: Make sure the zip includes `entities/`.
- **Need to re-extract**: If `requireRestartForReextract=true`, restart the server after running `/worldrestore extract`.

## Build

```bash
./gradlew build
```

The built jar will be in `build/libs/`.
