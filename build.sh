#!/usr/bin/env bash
set -e

JAVAC="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/javac"
JAVA="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/java"
JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-gamma/bin/jar"

INSTANCE_DIR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Hexxit-Remix-0.1.3/minecraft"
MC_JAR="$INSTANCE_DIR/bin/minecraft.jar"
FORGE_JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/libraries/net/minecraftforge/forge/1.5.2-7.8.1.737/forge-1.5.2-7.8.1.737-universal.jar"
CLIENT_JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/libraries/com/mojang/minecraft/1.5.2/minecraft-1.5.2-client.jar"
if [ ! -f "$MC_JAR" ]; then
    MC_JAR="$FORGE_JAR:$CLIENT_JAR"
fi
COREMOD_JAR="$INSTANCE_DIR/coremods/playercoreapi_1.5.2_1.0.jar"
ASM_JAR="$INSTANCE_DIR/lib/asm-all-4.1.jar"
LWJGL_JAR="/home/soko/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/libraries/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"

BASE_JAR="/home/soko/Downloads/aether_1.5.2_1.0_patched.jar"
TARGET_JAR="mods/aether_1.5.2_1.0_patched_1.0.4.jar"
CLIENT_MODS_DIR="$INSTANCE_DIR/mods"

echo "=== Building Aether II Hexxit Patched Jar v1.0.4 (Smart Moving, Lighting & Accessory Fix) ==="

mkdir -p /tmp/build_out
mkdir -p mods

# 1. Compile AetherMcpcAdapter and AetherClientHelper with Java 7 target (bytecode version 51)
echo "[1/4] Compiling AetherMcpcAdapter & AetherClientHelper (Java 7 target)..."
$JAVAC -source 7 -target 7 -cp "$MC_JAR:$COREMOD_JAR:$BASE_JAR:$LWJGL_JAR" -d /tmp/build_out src/AetherClientHelper.java src/AetherMcpcAdapter.java

# 2. Compile PatchAetherPlayerAccess
echo "[2/4] Compiling ASM Patcher..."
$JAVAC -cp "$ASM_JAR" -d /tmp/build_out src/PatchAetherPlayerAccess.java

# 3. Run ASM Patcher to inject dual-check logic into target jar
echo "[3/4] Patching Aether, CommonProxy, AetherPlayerTracker, and SlotMoreArmor..."
$JAVA -cp "/tmp/build_out:$ASM_JAR:$MC_JAR:$COREMOD_JAR:$BASE_JAR" PatchAetherPlayerAccess "$BASE_JAR" "$TARGET_JAR"

# 4. Inject AetherMcpcAdapter and AetherClientHelper into the target mod JAR
echo "[4/4] Injecting classes into $TARGET_JAR..."
cd /tmp/build_out
$JAR uf "/home/soko/Documents/antigravity/goofy-hypatia/$TARGET_JAR" AetherMcpcAdapter*.class net/aetherteam/aether/client/AetherClientHelper*.class
cd /home/soko/Documents/antigravity/goofy-hypatia

# Deploy to client instance
if [ -d "$CLIENT_MODS_DIR" ]; then
    echo "Deploying to client instance: $CLIENT_MODS_DIR..."
    rm -f "$CLIENT_MODS_DIR"/aether_1.5.2_1.0.*_patched.jar
    rm -f "$CLIENT_MODS_DIR"/aether_1.5.2_1.0_patched_*.jar
    cp "$TARGET_JAR" "$CLIENT_MODS_DIR/aether_1.5.2_1.0_patched_1.0.4.jar"
    echo "Deployed aether_1.5.2_1.0_patched_1.0.4.jar to client."
fi

echo "=== Build Complete: $TARGET_JAR ==="
