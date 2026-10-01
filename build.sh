#!/usr/bin/env bash
set -e

JAVAC="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/javac"
JAVA="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/java"
JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/jar"

MC_JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Hexxit-Remix-0.1.2/minecraft/bin/minecraft.jar"
MOD_JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Hexxit-Remix-0.1.2/minecraft/mods/aether_1.5.2_1.0_patched.jar"
LIBS_CP=$(cat /tmp/libs_classpath.txt | tr ":" "\n" | grep -v "minecraft-26.2" | tr "\n" ":")

echo "=== Building Aether II Hexxit Patched Jar ==="

mkdir -p /tmp/build_out
mkdir -p mods

# 1. Compile AetherInventoryAdapter
echo "[1/4] Compiling AetherInventoryAdapter..."
$JAVAC -source 8 -target 8 -cp "$MC_JAR:$LIBS_CP" -d /tmp/build_out src/AetherInventoryAdapter.java

# 2. Compile and run Patchers
echo "[2/4] Compiling ASM Patchers..."
$JAVAC -source 8 -target 8 -cp ":/tmp/build_out:$MC_JAR:$LIBS_CP" -d /tmp/build_out src/Patch*.java src/Build*.java

echo "[3/4] Running ASM Patchers..."
# Jukebox / Music patcher
$JAVA -cp ":/tmp/build_out:/tmp/cmp_patched:$MC_JAR:$LIBS_CP" PatchAetherMusicConfig

# Player Access / ClassCastException patcher
echo "[4/4] Running Player Access Patcher..."
$JAVA -cp ":/tmp/build_out:$MC_JAR:$LIBS_CP:/home/soko/Downloads/bed-mod/libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar:/home/soko/Downloads/bed-mod/libraries/org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar" PatchAetherPlayerAccess "$MOD_JAR" "$MOD_JAR"

# 3. Update Patched Mod JAR
cd /tmp/music_patch_out 2>/dev/null || true
if [ -d "/tmp/music_patch_out" ]; then
    $JAR uf "$MOD_JAR" net/aetherteam/aether/sound/JukeboxData.class net/aetherteam/aether/sound/JukeboxPlayer.class net/aetherteam/mainmenu_api/MenuBaseConfig.class net/aetherteam/mainmenu_api/JukeboxPlayer.class 2>/dev/null || true
fi

if [ -d "/tmp/it_patch_out2" ]; then
    cd /tmp/it_patch_out2
    $JAR uf "$MOD_JAR" invtweaks/InvTweaksObfuscation.class 2>/dev/null || true
fi

cp "$MOD_JAR" /home/soko/Games/Servers/Minecraft/Hexxit_Remix_Server_v0.1.3/mods/aether_1.5.2_1.0_patched.jar 2>/dev/null || true
cp "$MOD_JAR" /home/soko/.local/share/PrismLauncher/instances/Hexxit-Remix-0.1.3/minecraft/mods/aether_1.5.2_1.0_patched.jar 2>/dev/null || true

echo "=== Build Complete: mods/aether_1.5.2_1.0_patched.jar ==="
