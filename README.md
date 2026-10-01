# Aether II 1.5.2 Hexxit Remix Patched Mod

Bytecode patches to the Aether II 1.5.2 mod for Minecraft 1.5.2 (Hexxit Remix).

## Patched Features

1. **Vanilla Creative Survival Inventory Layout**:
   - Restores vanilla 9-slot hotbar and 27-slot inventory layout in the Creative Survival inventory tab without overlapping Aether accessory icons.
   - Preserves Aether accessory slots and tabs on the dedicated Aether inventory screen.

2. **Inventory Tweaks Button Restoration**:
   - Restores the Inventory Tweaks sorting button (`...`) in the top-right corner of the Creative Survival inventory screen.

3. **Configurable Aether Music Playback (`onlyPlayInAether`)**:
   - Adds `onlyPlayInAether` config setting to suppress Aether background music when the player is not currently in the Aether dimension (Dimension 3).
   - Configurable in `MenuAPI.properties` (`onlyPlayInAether=true`) or `config/Aether II.cfg` under `general { B:onlyPlayInAether=true }`.
   - Logs `[Aether Music Patch]` configuration status to console/logs on startup.

## Files & Sources

- Patched mod JAR: `mods/aether_1.5.2_1.0_patched.jar`
- Patch ASM sources: `src/`

