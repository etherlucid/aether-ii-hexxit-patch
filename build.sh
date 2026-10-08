#!/usr/bin/env bash
set -e

JAVAC="javac"
JAVA="java"
JAR="jar"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTANCE_DIR="/home/soko/.local/share/PrismLauncher/instances/Hexxit-Remix-0.1.4/minecraft"
FORGE_JAR="/home/soko/.local/share/PrismLauncher/libraries/net/minecraftforge/forge/1.5.2-7.8.1.737/forge-1.5.2-7.8.1.737-universal.jar"
CLIENT_JAR="/home/soko/.local/share/PrismLauncher/libraries/com/mojang/minecraft/1.5.2/minecraft-1.5.2-client.jar"
MC_JAR="$FORGE_JAR:$CLIENT_JAR"
COREMOD_JAR="$INSTANCE_DIR/coremods/playercoreapi_1.5.2_1.0.jar"
ASM_JAR="$INSTANCE_DIR/lib/asm-all-4.1.jar"
LWJGL_JAR="/home/soko/.local/share/PrismLauncher/libraries/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"

BASE_JAR="/home/soko/Documents/Projects/Ludum/Game/Minecraft/aether_1.5.2_1.0_patched.jar"
if [ ! -f "$BASE_JAR" ]; then
    BASE_JAR="$INSTANCE_DIR/mods/aether_1.5.2_1.0_patched_1.2.0.jar"
fi

TARGET_JAR="$SCRIPT_DIR/mods/aether_1.5.2_1.0_patched_1.2.0.jar"
CLIENT_MODS_DIR="$INSTANCE_DIR/mods"

echo "=== Building Aether II Hexxit Patched Jar v1.2.0 (Key 'I' Dispatcher, Dynamic Tabs & Smart Moving Fixes) ==="

rm -rf /tmp/build_out
mkdir -p /tmp/build_out
mkdir -p "$SCRIPT_DIR/mods"

# 1. Compile AetherClientHelper and AetherInventoryAdapter with Java 7 target (bytecode version 51)
echo "[1/4] Compiling AetherClientHelper and AetherInventoryAdapter (Java 7 target)..."
$JAVAC -source 7 -target 7 -cp "$MC_JAR:$COREMOD_JAR:$BASE_JAR:$LWJGL_JAR" -d /tmp/build_out src/AetherClientHelper.java src/AetherInventoryAdapter.java

# 2. Compile ASM Patcher
echo "[2/4] Compiling ASM Patcher..."
$JAVAC -cp "$ASM_JAR" -d /tmp/build_out src/PatchAetherPlayerAccess.java

# 3. Run ASM Patcher to inject client-side fixes into target jar
echo "[3/4] Patching SlotMoreArmor, RenderPlayerBaseAether, GuiInventoryAether, and ClientTickHandler..."
$JAVA -cp "/tmp/build_out:$ASM_JAR:$MC_JAR:$COREMOD_JAR:$BASE_JAR" PatchAetherPlayerAccess "$BASE_JAR" "$TARGET_JAR"

# 4. Inject helper classes into the target mod JAR
echo "[4/4] Injecting AetherClientHelper and AetherInventoryAdapter into $TARGET_JAR..."
cd /tmp/build_out
$JAR uf "$TARGET_JAR" net/aetherteam/aether/client/AetherClientHelper*.class AetherInventoryAdapter*.class
cd "$SCRIPT_DIR"

# Deploy to client instance
if [ -d "$CLIENT_MODS_DIR" ]; then
    echo "Deploying to client instance: $CLIENT_MODS_DIR..."
    rm -f "$CLIENT_MODS_DIR"/aether_1.5.2_1.0.*_patched.jar
    rm -f "$CLIENT_MODS_DIR"/aether_1.5.2_1.0_patched_*.jar
    cp "$TARGET_JAR" "$CLIENT_MODS_DIR/aether_1.5.2_1.0_patched_1.2.0.jar"
    echo "Deployed aether_1.5.2_1.0_patched_1.2.0.jar to client."
fi

echo "=== Build Complete: $TARGET_JAR ==="
