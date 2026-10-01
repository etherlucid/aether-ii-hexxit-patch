# Patch Sources

These Java source files are the ASM bytecode patchers used to produce the patched JAR.

## Files

- **AetherInventoryAdapter.java** – Runtime adapter class injected into the JAR. Provides:
  - `fixCreativeSurvivalLayout()` – Repositions armor/inventory slots to vanilla layout
  - `removeAetherButtons()` – Removes Book of Lore / Social / Donator buttons
  - `isSameTab()` – Tab comparison helper replacing direct reference equality
  - `shouldReplaceSurvivalGui()` / `shouldReplaceCreativeGui()` – GUI detection helpers

- **BuildFinalPatchedGui.java** – ASM patcher for `GuiAetherContainerCreative.class`
- **PatchClientTickHandler.java** – ASM patcher for `ClientTickHandler.class`
- **PatchSlotAetherCreative.java** – ASM patcher for `SlotAetherCreativeInventory.class`
- **PatchAetherSlotHelper.java** – ASM patcher for `AetherSlotHelper.class`
- **PatchInvTweaksCreative.java** – ASM patcher for `invtweaks.InvTweaksObfuscation.class` (restores Inventory Tweaks GUI sorting buttons)
- **PatchAetherMusicConfig.java** – ASM patcher for `JukeboxData.class` & `JukeboxPlayer.class` (adds `onlyPlayInAether` config option)

## Building

Requires Java 8 (or 17 with `-source 8 -target 8`) and ASM library (asm-all-4.1.jar from the Minecraft instance libs).

