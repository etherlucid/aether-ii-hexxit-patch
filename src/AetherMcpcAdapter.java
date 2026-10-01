import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import sun.misc.Unsafe;

import net.aetherteam.aether.AetherCommonPlayerHandler;
import net.aetherteam.aether.PlayerBaseAetherServer;
import net.aetherteam.aether.containers.ContainerPlayerAether;
import net.aetherteam.aether.containers.InventoryAether;
import net.aetherteam.playercore_api.cores.PlayerCoreServer;

/**
 * MCPC+ compatibility adapter for Aether II 1.5.2.
 * Provides server-side PlayerBaseAetherServer allocation, ContainerPlayerAether (size 53)
 * attachment, and universal stream-based NBT persistence for MCPC+ servers.
 */
public class AetherMcpcAdapter {

    private static final Map<String, PlayerBaseAetherServer> mcpcPlayers =
        new ConcurrentHashMap<String, PlayerBaseAetherServer>();

    private static Unsafe unsafe;
    private static long playerFieldOffset = -1;
    private static Method nbtWriteMethod;
    private static Method nbtReadMethod;

    static {
        try {
            Field uf = Unsafe.class.getDeclaredField("theUnsafe");
            uf.setAccessible(true);
            unsafe = (Unsafe) uf.get(null);

            Field pf = PlayerCoreServer.class.getDeclaredField("player");
            playerFieldOffset = unsafe.objectFieldOffset(pf);
        } catch (Throwable t) {
            System.err.println(">>> AETHER MCPC+: Failed to initialize Unsafe: " + t);
            t.printStackTrace();
        }

        // Initialize universal NBT stream methods via reflection
        try {
            Class<?> ccClass = null;
            for (String name : new String[]{"cc", "net.minecraft.nbt.CompressedStreamTools"}) {
                try {
                    ccClass = Class.forName(name);
                    break;
                } catch (Throwable ignored) {}
            }
            if (ccClass != null) {
                for (Method m : ccClass.getMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 2 && OutputStream.class.isAssignableFrom(p[1])) {
                        nbtWriteMethod = m;
                    } else if (p.length == 1 && InputStream.class.isAssignableFrom(p[0])) {
                        nbtReadMethod = m;
                    }
                }
            }
            System.out.println(">>> AETHER MCPC+: NBT writeMethod=" + nbtWriteMethod + ", readMethod=" + nbtReadMethod);
        } catch (Throwable t) {
            System.err.println(">>> AETHER MCPC+: Failed to initialize NBT methods: " + t);
            t.printStackTrace();
        }
    }

    /**
     * Obtains or creates a PlayerBaseAetherServer instance for an MCPC+ EntityPlayerMP.
     */
    public static PlayerBaseAetherServer getServerPlayer(jc player) {
        if (player == null) return null;
        String username = player.bS;
        if (username == null) return null;

        PlayerBaseAetherServer base = mcpcPlayers.get(username);
        if (base == null) {
            try {
                if (unsafe == null) {
                    System.err.println(">>> AETHER MCPC+: Unsafe is not available!");
                    return null;
                }
                base = (PlayerBaseAetherServer) unsafe.allocateInstance(PlayerBaseAetherServer.class);
                if (playerFieldOffset != -1) {
                    unsafe.putObject(base, playerFieldOffset, player);
                }
                base.inv = new InventoryAether(player);
                base.playerHandler = new AetherCommonPlayerHandler(player);
                base.mountInput = new ArrayList();
                base.extendedReachItems = new ArrayList();
                base.maxHealth = 20;

                // Load saved NBT data from disk if present
                loadPlayerData(player, base);

                mcpcPlayers.put(username, base);
                System.out.println(">>> AETHER MCPC+: Created PlayerBaseAetherServer for " + username);
            } catch (Throwable t) {
                System.err.println(">>> AETHER MCPC+: Error allocating PlayerBaseAetherServer: " + t);
                t.printStackTrace();
                return null;
            }
        } else {
            // Update player reference upon respawn or reconnection
            if (unsafe != null && playerFieldOffset != -1) {
                unsafe.putObject(base, playerFieldOffset, player);
            }
            if (base.playerHandler != null) {
                base.playerHandler.player = player;
            }
            if (base.inv != null) {
                base.inv.player = player;
            }
        }

        // Configure player craftingInventory (bL) and openContainer (bM) to ContainerPlayerAether (size 53)
        if (!(player.bL instanceof ContainerPlayerAether)) {
            try {
                boolean isServer = (player.q != null) ? !player.q.I : true;
                ContainerPlayerAether cpa = new ContainerPlayerAether(
                    player.bK,
                    base.inv,
                    isServer,
                    player,
                    base.playerHandler
                );
                player.bL = cpa;
                player.bM = cpa;
                System.out.println(">>> AETHER MCPC+: Configured ContainerPlayerAether for " + username);
            } catch (Throwable t) {
                System.err.println(">>> AETHER MCPC+: Error configuring ContainerPlayerAether: " + t);
                t.printStackTrace();
            }
        }

        return base;
    }

    /**
     * Called when an MCPC+ player logs out. Saves accessory data to disk.
     */
    public static void onPlayerLogout(jc player) {
        if (player == null) return;
        String username = player.bS;
        if (username == null) return;

        System.out.println(">>> AETHER MCPC+: onPlayerLogout called for " + username);
        PlayerBaseAetherServer base = mcpcPlayers.get(username);
        if (base != null) {
            savePlayerData(player, base);
            // Retain in mcpcPlayers so any subsequent logout handlers and packet dispatchers can still read base
        }
    }

    private static File getSaveDir(jc player) {
        try {
            if (player != null && player.q != null) {
                Object saveHandler = player.q.L();
                if (saveHandler instanceof ajt) {
                    return ((ajt) saveHandler).b();
                }
                if (saveHandler != null) {
                    try {
                        Method m = saveHandler.getClass().getMethod("b");
                        Object res = m.invoke(saveHandler);
                        if (res instanceof File) return (File) res;
                    } catch (Throwable ignored) {}
                    try {
                        Method m = saveHandler.getClass().getMethod("getWorldDirectory");
                        Object res = m.invoke(saveHandler);
                        if (res instanceof File) return (File) res;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            System.err.println(">>> AETHER MCPC+: Error determining save directory: " + t);
        }
        return new File(".");
    }

    public static void savePlayerData(jc player, PlayerBaseAetherServer base) {
        if (player == null || base == null) return;
        try {
            File worldDir = getSaveDir(player);
            File aetherDir = new File(worldDir, "aether_data");
            aetherDir.mkdirs();
            File file = new File(aetherDir, player.bS + ".dat");

            bs nbt = new bs();
            base.b(nbt);

            if (nbtWriteMethod != null) {
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    nbtWriteMethod.invoke(null, nbt, fos);
                } finally {
                    fos.close();
                }
                System.out.println(">>> AETHER MCPC+: Successfully saved data for " + player.bS + " (" + file.length() + " bytes)");
            } else {
                System.err.println(">>> AETHER MCPC+: Cannot save data: nbtWriteMethod is null!");
            }
        } catch (Throwable t) {
            System.err.println(">>> AETHER MCPC+: Error saving player data for " + player.bS + ": " + t);
            t.printStackTrace();
        }
    }

    public static void loadPlayerData(jc player, PlayerBaseAetherServer base) {
        if (player == null || base == null) return;
        try {
            File worldDir = getSaveDir(player);
            File aetherDir = new File(worldDir, "aether_data");
            File file = new File(aetherDir, player.bS + ".dat");
            if (!file.exists()) {
                System.out.println(">>> AETHER MCPC+: No existing save data found for " + player.bS);
                return;
            }

            if (nbtReadMethod != null) {
                FileInputStream fis = new FileInputStream(file);
                Object nbt;
                try {
                    nbt = nbtReadMethod.invoke(null, fis);
                } finally {
                    fis.close();
                }
                if (nbt instanceof bs) {
                    base.a((bs) nbt);
                    System.out.println(">>> AETHER MCPC+: Successfully loaded data for " + player.bS);
                }
            }
        } catch (Throwable t) {
            System.err.println(">>> AETHER MCPC+: Error loading player data for " + player.bS + ": " + t);
            t.printStackTrace();
        }
    }
}
