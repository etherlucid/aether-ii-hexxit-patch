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
        return false;
    }

    public static void initAetherGui(Object guiObj, Object playerObj) {
        if (guiObj == null || playerObj == null) return;
        try {
            // Set static player field on GuiInventoryAether
            Class<?> guiClass = guiObj.getClass();
            try {
                Field playerField = guiClass.getDeclaredField("player");
                playerField.setAccessible(true);
                playerField.set(null, playerObj);
            } catch (Throwable t) {}

            // Get player.openContainer (bL / openContainer)
            Object openContainer = null;
            for (Field f : playerObj.getClass().getDeclaredFields()) {
                String fname = f.getName();
                if (fname.equals("bL") || fname.equals("openContainer") || fname.equals("field_71070_bA")) {
                    f.setAccessible(true);
                    openContainer = f.get(playerObj);
                    if (openContainer != null) break;
                }
            }
            if (openContainer == null) {
                for (Field f : playerObj.getClass().getDeclaredFields()) {
                    String fname = f.getName();
                    if (fname.equals("bK") || fname.equals("inventoryContainer") || fname.equals("field_71069_bz")) {
                        f.setAccessible(true);
                        openContainer = f.get(playerObj);
                        if (openContainer != null) break;
                    }
                }
            }

            // Set guiObj.inventorySlots (d / inventorySlots) = openContainer
            if (openContainer != null) {
                Class<?> clazz = guiObj.getClass();
                while (clazz != null && clazz != Object.class) {
                    for (Field f : clazz.getDeclaredFields()) {
                        String fname = f.getName();
                        if (fname.equals("d") || fname.equals("inventorySlots") || fname.equals("field_75151_b") || fname.equals("slots")) {
                            f.setAccessible(true);
                            f.set(guiObj, openContainer);
                            break;
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
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

    private static boolean checkHasKnapsack() {
        try {
            Class<?> tproxyClass = Class.forName("mods.tinker.tconstruct.client.TProxyClient");
            Field armorField = tproxyClass.getField("armorExtended");
            Object armorObj = armorField.get(null);
            if (armorObj != null) {
                Field invField = armorObj.getClass().getField("inventory");
                Object[] invArray = (Object[]) invField.get(armorObj);
                if (invArray != null && invArray.length > 2 && invArray[2] != null) {
                    return true;
                }
            }
        } catch (Throwable t) {}
        return false;
    }

    private static int getGuiLeft(Object guiObj) {
        if (guiObj == null) return 0;
        int width = 0;
        int xSize = 176;

        Class<?> clazz = guiObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                String fname = f.getName();
                f.setAccessible(true);
                try {
                    if (fname.equals("e") || fname.equals("guiLeft") || fname.equals("field_74198_a")) {
                        int v = f.getInt(guiObj);
                        if (v > 0) return v;
                    }
                    if (fname.equals("g") || fname.equals("width") || fname.equals("field_73880_f")) {
                        int w = f.getInt(guiObj);
                        if (w > 0) width = w;
                    }
                    if (fname.equals("b") || fname.equals("xSize") || fname.equals("field_74199_b")) {
                        int x = f.getInt(guiObj);
                        if (x > 0) xSize = x;
                    }
                } catch (Throwable t) {}
            }
            clazz = clazz.getSuperclass();
        }

        if (width > 0) {
            if (xSize <= 0) xSize = 176;
            return (width - xSize) / 2;
        }
        return 0;
    }

    private static int getGuiTop(Object guiObj) {
        if (guiObj == null) return 0;
        int height = 0;
        int ySize = 166;

        Class<?> clazz = guiObj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                String fname = f.getName();
                f.setAccessible(true);
                try {
                    if (fname.equals("o") || fname.equals("guiTop") || fname.equals("field_74197_b")) {
                        int v = f.getInt(guiObj);
                        if (v > 0) return v;
                    }
                    if (fname.equals("h") || fname.equals("height") || fname.equals("field_73881_g")) {
                        int h = f.getInt(guiObj);
                        if (h > 0) height = h;
                    }
                    if (fname.equals("c") || fname.equals("ySize") || fname.equals("field_74198_c")) {
                        int y = f.getInt(guiObj);
                        if (y > 0) ySize = y;
                    }
                } catch (Throwable t) {}
            }
            clazz = clazz.getSuperclass();
        }

        if (height > 0) {
            if (ySize <= 0) ySize = 166;
            return (height - ySize) / 2;
        }
        return 0;
    }

    public static void attachTabs(Object guiObj, boolean isAether) {
        if (guiObj == null) return;
        String name = guiObj.getClass().getName();

        // Do not attach survival tabs to Creative Inventory screens!
        if (name.equals("ayy") || name.contains("GuiContainerCreative") || name.contains("GuiAetherContainerCreative")) {
            return;
        }

        // Check if current screen is a survival inventory screen or GuiInventoryAether
        boolean isSurvivalScreen = name.equals("azg") || 
                                   name.endsWith(".GuiInventory") || 
                                   name.contains("ArmorExtendedGui") || 
                                   name.contains("KnapsackGui") || 
                                   name.contains("GuiInventoryAether") || 
                                   isAether;

        if (!isSurvivalScreen) return;

        try {
            Field buttonListField = null;

            Class<?> clazz = guiObj.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    String fname = f.getName();
                    if (fname.equals("k") || fname.equals("buttonList") || fname.equals("field_73887_h")) {
                        buttonListField = f;
                        buttonListField.setAccessible(true);
                    }
                }
                clazz = clazz.getSuperclass();
            }

            if (buttonListField == null) return;
            List buttonList = (List) buttonListField.get(guiObj);
            if (buttonList == null) return;

            int guiLeft = getGuiLeft(guiObj);
            int guiTop = getGuiTop(guiObj);
            int tabY = guiTop - 28;

            boolean hasKnapsack = checkHasKnapsack();

            // Determine active tab index (0=Vanilla, 1=Tinkers Armor, 2=Knapsack, 3=Aether)
            int activeTab = 0;
            if (isAether || name.contains("GuiInventoryAether")) {
                activeTab = 3;
            } else if (name.contains("ArmorExtendedGui")) {
                activeTab = 1;
            } else if (name.contains("KnapsackGui")) {
                activeTab = 2;
            }

            // Remove old tab buttons from buttonList
            java.util.Iterator it = buttonList.iterator();
            while (it.hasNext()) {
                Object btn = it.next();
                if (btn != null) {
                    String bname = btn.getClass().getName();
                    if (btn instanceof GuiAetherTab || bname.contains("GuiAetherTab") || bname.contains("InventoryTab")) {
                        it.remove();
                    }
                }
            }

            // Dynamically instantiate and attach tab buttons
            int tabX = guiLeft;
            int colIndex = 0;

            // Tab 0: Vanilla Inventory (8001)
            buttonList.add(new GuiAetherTab(8001, tabX, tabY, 0, colIndex++, activeTab == 0));
            tabX += 28;

            // Tab 1: Tinkers Armor (8002)
            buttonList.add(new GuiAetherTab(8002, tabX, tabY, 1, colIndex++, activeTab == 1));
            tabX += 28;

            // Tab 3: Aether Accessories (8004)
            buttonList.add(new GuiAetherTab(8004, tabX, tabY, 3, colIndex++, activeTab == 3));
            tabX += 28;

            // Tab 2: Knapsack (8003) - if equipped
            if (hasKnapsack) {
                buttonList.add(new GuiAetherTab(8003, tabX, tabY, 2, colIndex++, activeTab == 2));
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
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

        // 2x2 Crafting Result (0) & Matrix (1..4)
        if (slots.size() > 0) setSlotPos(slots.get(0), 144, 36);
        if (slots.size() > 1) setSlotPos(slots.get(1), 88, 26);
        if (slots.size() > 2) setSlotPos(slots.get(2), 106, 26);
        if (slots.size() > 3) setSlotPos(slots.get(3), 88, 44);
        if (slots.size() > 4) setSlotPos(slots.get(4), 106, 44);

        // Armor Slots (5..8)
        if (slots.size() > 5) setSlotPos(slots.get(5), 8, 8);
        if (slots.size() > 6) setSlotPos(slots.get(6), 8, 26);
        if (slots.size() > 7) setSlotPos(slots.get(7), 8, 44);
        if (slots.size() > 8) setSlotPos(slots.get(8), 8, 62);

        // Main Inventory Slots (9..35)
        for (int i = 9; i < Math.min(36, slots.size()); i++) {
            int k = i - 9;
            int col = k % 9;
            int row = k / 9;
            setSlotPos(slots.get(i), 8 + col * 18, 84 + row * 18);
        }

        // Hotbar Slots (36..44)
        for (int i = 36; i < Math.min(45, slots.size()); i++) {
            int col = i - 36;
            setSlotPos(slots.get(i), 8 + col * 18, 142);
        }

        // Extra Mod / Aether Accessory Slots (>= 45) -> Hide offscreen when in Vanilla tab
        for (int i = 45; i < slots.size(); i++) {
            setSlotPos(slots.get(i), -9999, -9999);
        }
    }

    public static void setAetherLayout(Object containerObj, boolean isAether) {
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
        if (slots == null || slots.isEmpty()) return;

        if (isAether) {
            // Crafting Result & 2x2 Matrix (Aether Layout)
            if (slots.size() > 0) setSlotPos(slots.get(0), 134, 62);
            if (slots.size() > 1) setSlotPos(slots.get(1), 125, 8);
            if (slots.size() > 2) setSlotPos(slots.get(2), 143, 8);
            if (slots.size() > 3) setSlotPos(slots.get(3), 125, 26);
            if (slots.size() > 4) setSlotPos(slots.get(4), 143, 26);

            // Armor Slots (Aether Layout)
            if (slots.size() > 5) setSlotPos(slots.get(5), 62, 8);
            if (slots.size() > 6) setSlotPos(slots.get(6), 62, 26);
            if (slots.size() > 7) setSlotPos(slots.get(7), 62, 44);
            if (slots.size() > 8) setSlotPos(slots.get(8), 62, 62);

            // Main Inventory Slots (9..35)
            for (int i = 9; i < Math.min(36, slots.size()); i++) {
                int k = i - 9;
                int col = k % 9;
                int row = k / 9;
                setSlotPos(slots.get(i), 8 + col * 18, 84 + row * 18);
            }

            // Hotbar Slots (36..44)
            for (int i = 36; i < Math.min(45, slots.size()); i++) {
                int col = i - 36;
                setSlotPos(slots.get(i), 8 + col * 18, 142);
            }

            // Aether Accessory Slots (45..56)
            int accIdx = 0;
            for (int col = 0; col < 3; col++) {
                for (int row = 0; row < 4; row++) {
                    int slotIndex = 45 + accIdx;
                    if (slotIndex < slots.size()) {
                        setSlotPos(slots.get(slotIndex), 62 + (col + 1) * 18, 8 + row * 18);
                    }
                    accIdx++;
                }
            }
        } else {
            // Vanilla Survival Layout
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
