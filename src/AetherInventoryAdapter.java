import java.util.List;
import java.lang.reflect.Field;
import net.aetherteam.aether.containers.SlotAetherCreativeInventory;
import net.aetherteam.aether.client.gui.GuiAetherContainerCreative;

public class AetherInventoryAdapter {

    public static int getTabIndex(Object tabObj) {
        if (tabObj == null) return -1;
        Class<?> clazz = tabObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    String fname = f.getName();
                    if (fname.equals("row") || fname.equals("tabIndex") || fname.equals("field_74313_n")) {
                        try {
                            f.setAccessible(true);
                            return f.getInt(tabObj);
                        } catch (Throwable t) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        try {
            java.lang.reflect.Method m = tabObj.getClass().getMethod("a");
            return ((Integer) m.invoke(tabObj)).intValue();
        } catch (Throwable t) {}
        try {
            java.lang.reflect.Method m = tabObj.getClass().getMethod("getTabIndex");
            return ((Integer) m.invoke(tabObj)).intValue();
        } catch (Throwable t) {}
        return -1;
    }

    public static boolean isSameTab(Object tab1, Object tab2) {
        if (tab1 == tab2) return true;
        if (tab1 == null || tab2 == null) return false;
        int i1 = getTabIndex(tab1);
        int i2 = getTabIndex(tab2);
        if (i1 != -1 && i2 != -1) {
            return i1 == i2;
        }
        return false;
    }

    public static boolean shouldReplaceSurvivalGui(Object screen) {
        if (screen == null) return true;
        String name = screen.getClass().getName();
        if (name.contains("GuiInventoryAether")) return false;
        if (name.contains("GuiChat") || 
            name.contains("GuiIngameMenu") || 
            name.contains("GuiOptions") || 
            name.contains("GuiMainMenu") || 
            name.contains("GuiEditSign") || 
            name.contains("GuiSleepMP") || 
            name.contains("GuiCommandBlock") || 
            name.contains("GuiScreenBook") ||
            name.contains("GuiGameOver")) {
            return false;
        }
        return true;
    }

    public static boolean shouldReplaceCreativeGui(Object screen) {
        if (screen == null) return false;
        if (screen.getClass().getName().contains("GuiAetherContainerCreative")) return false;
        String name = screen.getClass().getName();
        boolean matches = name.equals("ayy") || 
                          name.equals("net.minecraft.client.gui.inventory.GuiContainerCreative") || 
                          name.endsWith("GuiContainerCreative") ||
                          name.contains("GuiContainerCreative") ||
                          name.contains("GuiExtendedCreativeInv");
        if (matches) {
            System.out.println(">>> AETHER ADAPTER: Replacing Creative GUI (" + name + ") with GuiAetherContainerCreative <<<");
        }
        return matches;
    }

    public static boolean isVanillaCreativeGui(Object screen) {
        return shouldReplaceCreativeGui(screen);
    }

    public static Object getCurrentScreen() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = null;
            try {
                java.lang.reflect.Method m = mcClass.getMethod("getMinecraft");
                mc = m.invoke(null);
            } catch (Throwable t) {
                try {
                    java.lang.reflect.Method m = mcClass.getMethod("A_");
                    mc = m.invoke(null);
                } catch (Throwable t2) {
                    for (Field f : mcClass.getDeclaredFields()) {
                        if (f.getType() == mcClass && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                            f.setAccessible(true);
                            mc = f.get(null);
                            if (mc != null) break;
                        }
                    }
                }
            }
            if (mc == null) return null;
            for (Field f : mc.getClass().getDeclaredFields()) {
                String fname = f.getName();
                if (fname.equals("s") || fname.equals("currentScreen") || fname.equals("field_71462_r")) {
                    f.setAccessible(true);
                    return f.get(mc);
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    private static List getSlots(Object containerObj) {
        if (containerObj == null) return null;
        Class<?> clazz = containerObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    String fname = f.getName();
                    if (fname.equals("c") || fname.equals("inventorySlots") || fname.equals("field_75151_b") || fname.equals("slots")) {
                        try {
                            f.setAccessible(true);
                            List list = (List) f.get(containerObj);
                            if (list != null) return list;
                        } catch (Throwable t) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static void setSlots(Object containerObj, List slots) {
        if (containerObj == null) return;
        Class<?> clazz = containerObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    String fname = f.getName();
                    if (fname.equals("c") || fname.equals("inventorySlots") || fname.equals("field_75151_b") || fname.equals("slots")) {
                        try {
                            f.setAccessible(true);
                            f.set(containerObj, slots);
                            return;
                        } catch (Throwable t) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public static void setGuiHeight(Object guiObj, int height) {
        if (guiObj == null) return;
        Class<?> clazz = guiObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    String name = f.getName();
                    if (name.equals("c") || name.equals("ySize") || name.equals("field_74199_b")) {
                        try {
                            f.setAccessible(true);
                            f.setInt(guiObj, height);
                        } catch (Throwable t) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public static void removeAetherButtons(Object guiObj) {
        if (guiObj == null) return;
        try {
            Class<?> clazz = guiObj.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())) {
                        String name = f.getName();
                        if (name.equals("k") || name.equals("buttonList") || name.equals("field_73887_h")) {
                            f.setAccessible(true);
                            List list = (List) f.get(guiObj);
                            if (list != null && !list.isEmpty()) {
                                java.util.Iterator it = list.iterator();
                                while (it.hasNext()) {
                                    Object b = it.next();
                                    if (b == null) continue;
                                    int id = -1;
                                    try {
                                        Field idField = b.getClass().getDeclaredField("f");
                                        idField.setAccessible(true);
                                        id = idField.getInt(b);
                                    } catch (Throwable t1) {
                                        try {
                                            Field idField = b.getClass().getDeclaredField("id");
                                            idField.setAccessible(true);
                                            id = idField.getInt(b);
                                        } catch (Throwable t2) {}
                                    }
                                    if (id == 5 || id == 6 || id == 7) {
                                        it.remove();
                                    }
                                }
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable t) {}
    }

    private static void setSlotPos(Object slotObj, int x, int y) {
        if (slotObj == null) return;
        Class<?> clazz = slotObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                String fname = f.getName();
                if (fname.equals("h") || fname.equals("xDisplayPosition") || fname.equals("field_75223_e")) {
                    try {
                        f.setAccessible(true);
                        f.setInt(slotObj, x);
                    } catch (Throwable t) {}
                }
                if (fname.equals("i") || fname.equals("yDisplayPosition") || fname.equals("field_75221_f")) {
                    try {
                        f.setAccessible(true);
                        f.setInt(slotObj, y);
                    } catch (Throwable t) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static Object getSlotInventory(Object slotObj) {
        if (slotObj == null) return null;
        Class<?> clazz = slotObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                String fname = f.getName();
                if (fname.equals("d") || fname.equals("inventory") || fname.equals("field_75224_c")) {
                    try {
                        f.setAccessible(true);
                        return f.get(slotObj);
                    } catch (Throwable t) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static int getSlotIndex(Object slotObj) {
        if (slotObj == null) return -1;
        Class<?> clazz = slotObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                String fname = f.getName();
                if (fname.equals("a") || fname.equals("slotIndex") || fname.equals("field_75225_a")) {
                    try {
                        f.setAccessible(true);
                        return f.getInt(slotObj);
                    } catch (Throwable t) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return -1;
    }

    public static void applyVanillaSlots(Object containerObj) {
        if (containerObj == null) return;
        try {
            Object currentScreen = getCurrentScreen();
            if (currentScreen != null) {
                String name = currentScreen.getClass().getName();
                if (name.contains("GuiAetherContainerCreative") || isVanillaCreativeGui(currentScreen)) {
                    // DO NOT overwrite slot positions when Creative Survival Inventory is open!
                    return;
                }
            }
        } catch (Throwable t) {}

        List slots = getSlots(containerObj);
        if (slots == null) return;
        if (slots.size() > 0) setSlotPos(slots.get(0), 144, 36);
        if (slots.size() > 1) setSlotPos(slots.get(1), 88, 26);
        if (slots.size() > 2) setSlotPos(slots.get(2), 106, 26);
        if (slots.size() > 3) setSlotPos(slots.get(3), 88, 44);
        if (slots.size() > 4) setSlotPos(slots.get(4), 106, 44);

        if (slots.size() > 5) setSlotPos(slots.get(5), 8, 8);
        if (slots.size() > 6) setSlotPos(slots.get(6), 8, 26);
        if (slots.size() > 7) setSlotPos(slots.get(7), 8, 44);
        if (slots.size() > 8) setSlotPos(slots.get(8), 8, 62);

        for (int i = 9; i < slots.size(); i++) {
            setSlotPos(slots.get(i), -9999, -9999);
        }
    }

    public static void setAetherLayout(Object containerObj, boolean isAether) {
        if (containerObj == null) return;
        List slots = getSlots(containerObj);
        if (slots == null) return;

        if (isAether) {
            if (slots.size() > 0) setSlotPos(slots.get(0), 134, 62);
            if (slots.size() > 1) setSlotPos(slots.get(1), 125, 8);
            if (slots.size() > 2) setSlotPos(slots.get(2), 143, 8);
            if (slots.size() > 3) setSlotPos(slots.get(3), 125, 26);
            if (slots.size() > 4) setSlotPos(slots.get(4), 143, 26);

            if (slots.size() > 5) setSlotPos(slots.get(5), 62, 8);
            if (slots.size() > 6) setSlotPos(slots.get(6), 62, 26);
            if (slots.size() > 7) setSlotPos(slots.get(7), 62, 44);
            if (slots.size() > 8) setSlotPos(slots.get(8), 62, 62);
        } else {
            applyVanillaSlots(containerObj);
        }
    }

    public static void fixCreativeSurvivalLayout(Object guiObj, Object containerCreativeObj, Object playerContainerObj) {
        System.out.println(">>> AETHER ADAPTER: fixCreativeSurvivalLayout CALLED (Precision Vanilla Alignment) <<<");
        setGuiHeight(guiObj, 136);
        removeAetherButtons(guiObj);

        if (containerCreativeObj == null || playerContainerObj == null) return;
        List playerSlots = getSlots(playerContainerObj);
        if (playerSlots == null || playerSlots.isEmpty()) return;

        List creativeSlots = new java.util.ArrayList();
        GuiAetherContainerCreative gui = (GuiAetherContainerCreative) guiObj;

        for (int j = 0; j < playerSlots.size(); j++) {
            Object rawSlotObj = playerSlots.get(j);
            if (rawSlotObj == null) continue;

            SlotAetherCreativeInventory slotcreativeinventory =
                new SlotAetherCreativeInventory(gui, (ul) rawSlotObj, j);

            Object innerSlot = rawSlotObj;
            if (innerSlot instanceof SlotAetherCreativeInventory) {
                innerSlot = SlotAetherCreativeInventory.func_75240_a((SlotAetherCreativeInventory) innerSlot);
            }

            String innerName = (innerSlot != null) ? innerSlot.getClass().getName() : "";
            boolean isExtraSlot = innerName.contains("SlotMoreArmor") ||
                                  innerName.contains("ArmorExtended") ||
                                  innerName.contains("Accessory") ||
                                  innerName.contains("Knapsack") ||
                                  innerName.contains("ActiveSlot") ||
                                  innerName.contains("SlotOnlyTake");

            if (isExtraSlot) {
                setSlotPos(slotcreativeinventory, -2000, -2000);
                setSlotPos(rawSlotObj, -2000, -2000);
                creativeSlots.add(slotcreativeinventory);
                continue;
            }

            Object inv = (innerSlot != null) ? getSlotInventory(innerSlot) : null;
            int slotIdx = (innerSlot != null) ? getSlotIndex(innerSlot) : -1;
            String invName = (inv != null) ? inv.getClass().getName() : "";

            boolean isPlayerInv = invName.contains("InventoryPlayer") || invName.equals("lz");

            if (isPlayerInv && slotIdx >= 0) {
                // Hotbar slots (0..8 in InventoryPlayer) -> row y = 112
                if (slotIdx >= 0 && slotIdx < 9) {
                    int x = 9 + slotIdx * 18;
                    int y = 112;
                    setSlotPos(slotcreativeinventory, x, y);
                    setSlotPos(rawSlotObj, x, y);
                    creativeSlots.add(slotcreativeinventory);
                    continue;
                }
                // Main Inventory slots (9..35 in InventoryPlayer) -> rows y = 54, 72, 90
                if (slotIdx >= 9 && slotIdx < 36) {
                    int k = slotIdx - 9;
                    int l = k % 9;
                    int i1 = k / 9;
                    int x = 9 + l * 18;
                    int y = 54 + i1 * 18;
                    setSlotPos(slotcreativeinventory, x, y);
                    setSlotPos(rawSlotObj, x, y);
                    creativeSlots.add(slotcreativeinventory);
                    continue;
                }
                // Armor slots (36..39 in InventoryPlayer) -> aligned around player model (x=10, x=64)
                if (slotIdx >= 36 && slotIdx < 40) {
                    int x = 10;
                    int y = 6;
                    switch (slotIdx) {
                        case 39: x = 10; y = 6; break;   // Helmet
                        case 38: x = 10; y = 33; break;  // Chestplate
                        case 37: x = 64; y = 6; break;  // Leggings
                        case 36: x = 64; y = 33; break; // Boots
                    }
                    setSlotPos(slotcreativeinventory, x, y);
                    setSlotPos(rawSlotObj, x, y);
                    creativeSlots.add(slotcreativeinventory);
                    continue;
                }
            }

            // Crafting Result slot (j == 0) & 2x2 Crafting Matrix slots (j == 1..4) -> HIDE OFFSCREEN (unhoverable)
            if (j >= 0 && j < 5) {
                setSlotPos(slotcreativeinventory, -2000, -2000);
                setSlotPos(rawSlotObj, -2000, -2000);
                creativeSlots.add(slotcreativeinventory);
                continue;
            }

            // Any other extra slot -> hide offscreen
            setSlotPos(slotcreativeinventory, -2000, -2000);
            setSlotPos(rawSlotObj, -2000, -2000);
            creativeSlots.add(slotcreativeinventory);
        }

        System.out.println(">>> AETHER ADAPTER: Successfully built " + creativeSlots.size() + " precision vanilla creative slots <<<");
        setSlots(containerCreativeObj, creativeSlots);
    }
}
