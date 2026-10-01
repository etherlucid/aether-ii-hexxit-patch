#!/usr/bin/env bash
set -e

JAVAC="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/javac"
JAVA="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/java"
JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/jar"

MC_JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Hexxit-Remix-0.1.2/minecraft/bin/minecraft.jar"
MOD_JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Hexxit-Remix-0.1.2/minecraft/mods/aether_1.5.2_1.0_patched.jar"
LIBS_CP=$(cat /tmp/libs_classpath.txt | tr ":" "\n" | grep -v "minecraft-26.2" | tr "\n" ":")
ASM_CP="/home/soko/Downloads/bed-mod/libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar:/home/soko/Downloads/bed-mod/libraries/org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar"

echo "=== Building Aether II Hexxit Patched Jar ==="

mkdir -p /tmp/build_out
mkdir -p mods

# 1. Compile AetherInventoryAdapter
echo "[1/6] Compiling AetherInventoryAdapter..."
$JAVAC -source 8 -target 8 -cp "$MC_JAR:$LIBS_CP" -d /tmp/build_out src/AetherInventoryAdapter.java

# 2. Compile all ASM patchers
echo "[2/6] Compiling ASM Patchers..."
$JAVAC -source 8 -target 8 -cp ":/tmp/build_out:$MC_JAR:$LIBS_CP" -d /tmp/build_out src/Patch*.java src/Build*.java

# 3. Music config patcher
echo "[3/6] Running Music Config Patcher..."
$JAVA -cp ":/tmp/build_out:/tmp/cmp_patched:$MC_JAR:$LIBS_CP" PatchAetherMusicConfig

# 4. Player access patcher — dual-check MCPC+ fix
echo "[4/6] Running Player Access Patcher (MCPC+ dual-check)..."
$JAVA -cp ":/tmp/build_out:$MC_JAR:$LIBS_CP:$ASM_CP" PatchAetherPlayerAccess "$MOD_JAR" "$MOD_JAR"

# 5. Slot icon restore patcher — re-enables accessory slot icons in survival inventory
echo "[5/6] Running Slot Icon Restore Patcher..."
$JAVA -cp ":/tmp/build_out:$MC_JAR:$LIBS_CP:$ASM_CP" PatchSlotMoreArmor "$MOD_JAR" "$MOD_JAR"

# 6. AetherPlayerTracker null-guard — prevents NPE on logout/client-info-update when getServerPlayer() returns null (MCPC+)
echo "[6/6] Running AetherPlayerTracker Null-Guard Patcher..."
$JAVA -cp ":/tmp/build_out:$MC_JAR:$LIBS_CP:$ASM_CP" PatchAetherPlayerTracker "$MOD_JAR" "$MOD_JAR"

# Update JAR with music patch classes
cd /tmp/music_patch_out 2>/dev/null || true
if [ -d "/tmp/music_patch_out" ]; then
    $JAR uf "$MOD_JAR" net/aetherteam/aether/sound/JukeboxData.class net/aetherteam/aether/sound/JukeboxPlayer.class net/aetherteam/mainmenu_api/MenuBaseConfig.class net/aetherteam/mainmenu_api/JukeboxPlayer.class 2>/dev/null || true
fi

if [ -d "/tmp/it_patch_out2" ]; then
    cd /tmp/it_patch_out2
    $JAR uf "$MOD_JAR" invtweaks/InvTweaksObfuscation.class 2>/dev/null || true
fi

cp "$MOD_JAR" /home/soko/Games/Servers/Minecraft/Hexxit_Remix_Server_v0.1.3/mods/aether_1.5.2_1.0_patched.jar 2>/dev/null || true
cp "$MOD_JAR" /home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Hexxit-Remix-0.1.3/minecraft/mods/aether_1.5.2_1.0_patched.jar 2>/dev/null || true

echo "=== Build Complete: $MOD_JAR ==="
