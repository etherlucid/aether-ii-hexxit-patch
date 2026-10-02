package net.aetherteam.aether.client;

import cpw.mods.fml.client.FMLClientHandler;
import net.aetherteam.aether.client.gui.GuiInventoryAether;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class AetherClientHelper {

    // Reflection fields for ModelBiped (bbz)
    private static Field modelMiscField;
    private static Method getModelBipedMainMethod;
    private static Method getModelBipedMainFieldMethod;
    private static Method getMainModelFieldMethod;
    private static Field mainModelBhoField;
    private static Field modelBipedMainBhtField;
    private static Field renderPlayerField;
    private static Field bipedRightArmField;
    private static Field bipedLeftArmField;
    private static Field bipedBodyField;
    private static Field modelWingsField;
    private static Field wingLeftField;
    private static Field wingRightField;

    // Reflection fields for ModelRenderer (bdi)
    private static Field rotXField;
    private static Field rotYField;
    private static Field rotZField;
    private static Field pointXField;
    private static Field pointYField;
    private static Field pointZField;

    // Reflection fields for RenderPlayerAPI & Smart Moving
    private static Class<?> bhtClass;
    private static Method getRenderPlayerBaseMethod;
    private static Method getModelPlayerBaseMethod;
    private static Method getRenderModelsMethod;
    private static Method getOuterMethod;
    private static Method getRightArmMethod;
    private static Method getLeftArmMethod;
    private static Method getRightShoulderMethod;
    private static Method getLeftShoulderMethod;
    private static Method getBodyMethod;

    // Reflection fields for ModelPlayerAPI
    private static Method getAllInstancesMethod;

    // Reflection fields for RenderManager (bgy) and Render (bgz)
    private static Field instanceField;
    private static Field entityRenderMapField;
    private static Method getEntityRenderObjectMethod;
    private static Field renderManagerField;
    private static Class<?> sqClass;

    // Reflection fields for Minecraft and EntityLiving
    private static Method getMinecraftMethod;
    private static Field currentScreenField;
    private static Class<?> guiContainerClass;
    private static Field renderYawOffsetField;
    private static Field prevRenderYawOffsetField;

    // Reflection fields for OpenGlHelper (bkn)
    private static int defaultTexUnit = 33984; // GL13.GL_TEXTURE0
    private static int lightmapTexUnit = 33985; // GL13.GL_TEXTURE1
    private static Method setActiveTextureMethod;
    private static Method setLightmapCoordsMethod;

    // Reflection fields for Entity (mp)
    private static Method getBrightnessForRenderMethod;
    private static Method isBurningMethod;

    // Cached Smart Moving model
    private static volatile Object cachedSmartModel;

    // Debug logging
    private static long lastLogRotate = 0;
    private static long lastLogSync = 0;

    static {
        try {
            Class<?> rpbaClass = Class.forName("net.aetherteam.aether.client.RenderPlayerBaseAether");
            modelMiscField = findField(rpbaClass, "modelMisc");
            modelWingsField = findField(rpbaClass, "modelWings");

            Class<?> mawClass = findClass("net.aetherteam.aether.client.models.ModelAetherWings");
            if (mawClass != null) {
                wingLeftField = findField(mawClass, "wingLeft");
                wingRightField = findField(mawClass, "wingRight");
            }

            Class<?> pcrClass = Class.forName("net.aetherteam.playercore_api.cores.PlayerCoreRender");
            try {
                getModelBipedMainMethod = pcrClass.getMethod("getModelBipedMain");
            } catch (Throwable ignored) {}
            renderPlayerField = findField(pcrClass, "renderPlayer");

            bhtClass = findClass("bht", "net.minecraft.client.renderer.entity.RenderPlayer");
            if (bhtClass != null) {
                try { getRenderPlayerBaseMethod = bhtClass.getMethod("getRenderPlayerBase", String.class); } catch (Throwable ignored) {}
                try { getModelBipedMainFieldMethod = bhtClass.getMethod("getModelBipedMainField"); } catch (Throwable ignored) {}
                try { getMainModelFieldMethod = bhtClass.getMethod("getMainModelField"); } catch (Throwable ignored) {}
                modelBipedMainBhtField = findField(bhtClass, "field_77109_a", "a", "modelBipedMain");
            }

            Class<?> mpClass = findClass("ModelPlayer");
            if (mpClass != null) {
                try { getModelPlayerBaseMethod = mpClass.getMethod("getModelPlayerBase", String.class); } catch (Throwable ignored) {}
            }

            Class<?> mpApiClass = findClass("ModelPlayerAPI");
            if (mpApiClass != null) {
                try { getAllInstancesMethod = mpApiClass.getMethod("getAllInstances"); } catch (Throwable ignored) {}
            }

            Class<?> srrpbClass = findClass("net.smart.render.playerapi.SmartRenderRenderPlayerBase");
            if (srrpbClass != null) {
                try { getRenderModelsMethod = srrpbClass.getMethod("getRenderModels"); } catch (Throwable ignored) {}
            }

            Class<?> bhoClass = findClass("bho", "net.minecraft.client.renderer.entity.RenderLiving");
            if (bhoClass != null) {
                mainModelBhoField = findField(bhoClass, "field_77045_g", "i", "mainModel");
            }

            Class<?> bbzClass = findClass("bbz", "net.minecraft.client.model.ModelBiped");
            if (bbzClass != null) {
                bipedRightArmField = findField(bbzClass, "field_78112_f", "f", "bipedRightArm");
                bipedLeftArmField = findField(bbzClass, "field_78113_g", "g", "bipedLeftArm");
                bipedBodyField = findField(bbzClass, "field_78115_e", "e", "bipedBody");
            }

            Class<?> bdiClass = findClass("bdi", "net.minecraft.client.model.ModelRenderer");
            if (bdiClass != null) {
                rotXField = findField(bdiClass, "field_78795_f", "f", "rotateAngleX");
                rotYField = findField(bdiClass, "field_78796_g", "g", "rotateAngleY");
                rotZField = findField(bdiClass, "field_78808_h", "h", "rotateAngleZ");
                pointXField = findField(bdiClass, "field_78800_c", "c", "rotationPointX");
                pointYField = findField(bdiClass, "field_78797_d", "d", "rotationPointY");
                pointZField = findField(bdiClass, "field_78798_e", "e", "rotationPointZ");
            }

            Class<?> bgyClass = findClass("bgy", "net.minecraft.client.renderer.entity.RenderManager");
            if (bgyClass != null) {
                instanceField = findField(bgyClass, "field_78727_a", "a", "instance");
                entityRenderMapField = findField(bgyClass, "field_78729_o", "q", "entityRenderMap");
                getEntityRenderObjectMethod = findMethod(bgyClass, new Class<?>[]{Class.class}, "func_78715_a", "a", "getEntityClassRenderObject");
            }

            Class<?> bgzClass = findClass("bgz", "net.minecraft.client.renderer.entity.Render");
            if (bgzClass != null) {
                renderManagerField = findField(bgzClass, "field_76990_c", "b", "renderManager");
            }

            sqClass = findClass("sq", "net.minecraft.entity.player.EntityPlayer");

            Class<?> mcClass = findClass("net.minecraft.client.Minecraft");
            if (mcClass != null) {
                getMinecraftMethod = findMethod(mcClass, new Class<?>[0], "func_71410_x", "x", "getMinecraft");
                currentScreenField = findField(mcClass, "field_71462_r", "s", "currentScreen");
            }

            guiContainerClass = findClass("azb", "net.minecraft.client.gui.inventory.GuiContainer");

            Class<?> ngClass = findClass("ng", "net.minecraft.entity.EntityLiving");
            if (ngClass != null) {
                renderYawOffsetField = findField(ngClass, "field_70761_aq", "ay", "renderYawOffset");
                prevRenderYawOffsetField = findField(ngClass, "field_70760_ar", "az", "prevRenderYawOffset");
            }

            Class<?> bknClass = findClass("bkn", "net.minecraft.client.renderer.OpenGlHelper");
            if (bknClass != null) {
                Field dtuField = findField(bknClass, "field_77478_a", "a", "defaultTexUnit");
                if (dtuField != null) {
                    try { defaultTexUnit = dtuField.getInt(null); } catch (Throwable ignored) {}
                }
                Field ltuField = findField(bknClass, "field_77476_b", "b", "lightmapTexUnit");
                if (ltuField != null) {
                    try { lightmapTexUnit = ltuField.getInt(null); } catch (Throwable ignored) {}
                }
                setActiveTextureMethod = findMethod(bknClass, new Class<?>[]{int.class}, "func_77473_a", "a", "setActiveTexture");
                setLightmapCoordsMethod = findMethod(bknClass, new Class<?>[]{int.class, float.class, float.class}, "func_77475_a", "a", "setLightmapTextureCoords");
            }

            Class<?> entityClass = findClass("mp", "net.minecraft.entity.Entity");
            if (entityClass != null) {
                getBrightnessForRenderMethod = findMethod(entityClass, new Class<?>[]{float.class}, "func_70070_b", "b", "getBrightnessForRender");
                isBurningMethod = findMethod(entityClass, new Class<?>[0], "func_70027_ad", "ae", "isBurning");
            }

            System.out.println("[AetherClientHelper] Initialized fields: rotYField=" + rotYField +
                ", rotXField=" + rotXField + ", rotZField=" + rotZField +
                ", bipedRightArm=" + bipedRightArmField + ", bipedLeftArm=" + bipedLeftArmField +
                ", setActiveTexture=" + setActiveTextureMethod + ", defaultTexUnit=" + defaultTexUnit +
                ", lightmapTexUnit=" + lightmapTexUnit);
        } catch (Throwable t) {
            System.out.println("[AetherClientHelper] static init error: " + t);
        }
    }

    private static void logDebugRotate(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastLogRotate > 2500) {
            lastLogRotate = now;
            System.out.println("[AetherClientHelper-Rotate] " + msg);
        }
    }

    private static void logDebugSync(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastLogSync > 2500) {
            lastLogSync = now;
            System.out.println("[AetherClientHelper-Sync] " + msg);
        }
    }

    private static Class<?> findClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String... names) {
        if (clazz == null) return null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, Class<?>[] paramTypes, String... names) {
        if (clazz == null) return null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Method m = c.getDeclaredMethod(name, paramTypes);
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static boolean isAetherGuiOpen() {
        try {
            return FMLClientHandler.instance().isGUIOpen(GuiInventoryAether.class);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isGuiPreview(float partialTicks) {
        if (partialTicks == 1.0F) {
            if (isAetherGuiOpen()) return true;
            try {
                if (getMinecraftMethod != null && currentScreenField != null && guiContainerClass != null) {
                    Object mc = getMinecraftMethod.invoke(null);
                    if (mc != null) {
                        Object screen = currentScreenField.get(mc);
                        if (screen != null && guiContainerClass.isInstance(screen)) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static void initSmartModelMethods(Class<?> clazz) {
        if (getOuterMethod != null) return;
        try {
            getOuterMethod = clazz.getMethod("getOuter");
            try { getRightArmMethod = clazz.getMethod("getRightArm"); } catch (Throwable ignored) {}
            try { getLeftArmMethod = clazz.getMethod("getLeftArm"); } catch (Throwable ignored) {}
            try { getRightShoulderMethod = clazz.getMethod("getRightShoulder"); } catch (Throwable ignored) {}
            try { getLeftShoulderMethod = clazz.getMethod("getLeftShoulder"); } catch (Throwable ignored) {}
            try { getBodyMethod = clazz.getMethod("getBody"); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    /**
     * Resolves the active Smart Moving model instance (SmartRenderModelPlayerBase)
     * using ModelPlayerAPI.getAllInstances(), RenderManager, or the local render object.
     */
    public static Object getSmartModelBase(Object render) {
        if (cachedSmartModel != null) {
            return cachedSmartModel;
        }

        try {
            // Strategy 1: Query ModelPlayerAPI.getAllInstances() directly
            if (getAllInstancesMethod != null) {
                try {
                    Object[] all = (Object[]) getAllInstancesMethod.invoke(null);
                    if (all != null && all.length > 0) {
                        for (Object mp : all) {
                            if (mp != null && getModelPlayerBaseMethod != null) {
                                Object smartBase = getModelPlayerBaseMethod.invoke(mp, "Smart Render");
                                if (smartBase != null) {
                                    initSmartModelMethods(smartBase.getClass());
                                    cachedSmartModel = smartBase;
                                    System.out.println("[AetherClientHelper] Resolved Smart Moving model via ModelPlayerAPI.getAllInstances (" + smartBase.getClass().getName() + ")");
                                    return smartBase;
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    System.out.println("[AetherClientHelper] Error querying ModelPlayerAPI: " + t);
                }
            }

            // Strategy 2: Query primary RenderPlayer from RenderManager singleton
            Object rm = instanceField != null ? instanceField.get(null) : null;
            if (rm == null && render != null && renderManagerField != null) {
                try { rm = renderManagerField.get(render); } catch (Throwable ignored) {}
            }
            if (rm != null && sqClass != null) {
                Object primaryRP = null;
                if (getEntityRenderObjectMethod != null) {
                    try { primaryRP = getEntityRenderObjectMethod.invoke(rm, sqClass); } catch (Throwable ignored) {}
                }
                if (primaryRP == null && entityRenderMapField != null) {
                    try {
                        Map map = (Map) entityRenderMapField.get(rm);
                        if (map != null) primaryRP = map.get(sqClass);
                    } catch (Throwable ignored) {}
                }
                if (primaryRP != null) {
                    // Check RenderPlayerAPI on primary RenderPlayer
                    if (getRenderPlayerBaseMethod != null) {
                        try {
                            Object rpb = getRenderPlayerBaseMethod.invoke(primaryRP, "Smart Render");
                            if (rpb != null && getRenderModelsMethod != null) {
                                Object[] models = (Object[]) getRenderModelsMethod.invoke(rpb);
                                if (models != null && models.length > 0 && models[0] != null) {
                                    initSmartModelMethods(models[0].getClass());
                                    cachedSmartModel = models[0];
                                    System.out.println("[AetherClientHelper] Resolved Smart Moving model via RenderManager primary RenderPlayer (" + models[0].getClass().getName() + ")");
                                    return models[0];
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                    // Check mainModel on primary RenderPlayer
                    Object mainModel = null;
                    if (getModelBipedMainFieldMethod != null) {
                        try { mainModel = getModelBipedMainFieldMethod.invoke(primaryRP); } catch (Throwable ignored) {}
                    }
                    if (mainModel == null && mainModelBhoField != null) {
                        try { mainModel = mainModelBhoField.get(primaryRP); } catch (Throwable ignored) {}
                    }
                    if (mainModel != null && getModelPlayerBaseMethod != null) {
                        try {
                            Object smartBase = getModelPlayerBaseMethod.invoke(mainModel, "Smart Render");
                            if (smartBase != null) {
                                initSmartModelMethods(smartBase.getClass());
                                cachedSmartModel = smartBase;
                                System.out.println("[AetherClientHelper] Resolved Smart Moving model via RenderManager primary mainModel (" + smartBase.getClass().getName() + ")");
                                return smartBase;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            // Strategy 3: Query render instance directly
            if (render != null) {
                Object bhtObj = null;
                if (renderPlayerField != null) {
                    bhtObj = renderPlayerField.get(render);
                }
                if (bhtObj == null) {
                    bhtObj = render;
                }
                if (getRenderPlayerBaseMethod != null && bhtClass != null && bhtClass.isInstance(bhtObj)) {
                    try {
                        Object rpb = getRenderPlayerBaseMethod.invoke(bhtObj, "Smart Render");
                        if (rpb != null && getRenderModelsMethod != null) {
                            Object[] models = (Object[]) getRenderModelsMethod.invoke(rpb);
                            if (models != null && models.length > 0 && models[0] != null) {
                                initSmartModelMethods(models[0].getClass());
                                cachedSmartModel = models[0];
                                System.out.println("[AetherClientHelper] Resolved Smart Moving model via render (" + models[0].getClass().getName() + ")");
                                return models[0];
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            System.out.println("[AetherClientHelper] getSmartModelBase error: " + t);
        }
        return null;
    }

    private static Object getModelMisc(Object render) {
        if (modelMiscField != null) {
            try {
                return modelMiscField.get(render);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Called right after modelMisc.setRotationAngles(...) in RenderPlayerBaseAether.doRenderMisc.
     * Synchronizes modelMisc's right arm, left arm, and body with the Smart Moving player model.
     */
    public static void syncModel(Object render) {
        try {
            Object miscModel = getModelMisc(render);
            if (miscModel == null) return;

            Object smartModel = getSmartModelBase(render);
            if (smartModel != null) {
                Object rightShoulder = getRightShoulderMethod != null ? getRightShoulderMethod.invoke(smartModel) : null;
                Object leftShoulder = getLeftShoulderMethod != null ? getLeftShoulderMethod.invoke(smartModel) : null;
                Object rightArm = getRightArmMethod != null ? getRightArmMethod.invoke(smartModel) : null;
                Object leftArm = getLeftArmMethod != null ? getLeftArmMethod.invoke(smartModel) : null;
                Object body = getBodyMethod != null ? getBodyMethod.invoke(smartModel) : null;

                if (bipedRightArmField != null && rightArm != null) {
                    syncArm(rightShoulder, rightArm, bipedRightArmField.get(miscModel));
                }
                if (bipedLeftArmField != null && leftArm != null) {
                    syncArm(leftShoulder, leftArm, bipedLeftArmField.get(miscModel));
                }
                if (bipedBodyField != null && body != null) {
                    Object bodyDst = bipedBodyField.get(miscModel);
                    syncRenderer(body, bodyDst);
                    syncWings(render, bodyDst);
                }
                logDebugSync("Synced arms & body from Smart Moving (" + smartModel.getClass().getSimpleName() + ")");
                return;
            }

            // Fallback: sync from vanilla model if present
            Object mainModel = null;
            if (renderPlayerField != null) {
                Object rp = renderPlayerField.get(render);
                if (rp != null && getModelBipedMainMethod != null) {
                    mainModel = getModelBipedMainMethod.invoke(rp);
                }
            }
            if (mainModel == null && getModelBipedMainMethod != null) {
                mainModel = getModelBipedMainMethod.invoke(render);
            }
            if (mainModel != null) {
                if (bipedRightArmField != null) {
                    syncRenderer(bipedRightArmField.get(mainModel), bipedRightArmField.get(miscModel));
                }
                if (bipedLeftArmField != null) {
                    syncRenderer(bipedLeftArmField.get(mainModel), bipedLeftArmField.get(miscModel));
                }
                if (bipedBodyField != null) {
                    Object bodyDst = bipedBodyField.get(miscModel);
                    syncRenderer(bipedBodyField.get(mainModel), bodyDst);
                    syncWings(render, bodyDst);
                }
                logDebugSync("Fallback vanilla sync");
            }
        } catch (Throwable t) {
            logDebugSync("syncModel error: " + t);
        }
    }

    private static void syncArm(Object parentShoulder, Object srcArm, Object dstArm) {
        if (srcArm == null || dstArm == null) return;
        try {
            float rx = rotXField != null ? rotXField.getFloat(srcArm) : 0.0F;
            float ry = rotYField != null ? rotYField.getFloat(srcArm) : 0.0F;
            float rz = rotZField != null ? rotZField.getFloat(srcArm) : 0.0F;
            float px = pointXField != null ? pointXField.getFloat(srcArm) : 0.0F;
            float py = pointYField != null ? pointYField.getFloat(srcArm) : 0.0F;
            float pz = pointZField != null ? pointZField.getFloat(srcArm) : 0.0F;

            // In-game with Smart Moving: srcArm is a child of parentShoulder (so px == 0)
            // and parentShoulder holds the shoulder position (-5.0F / 5.0F) plus crawl offsets.
            // In GUI preview with Smart Moving: isInventory is true, Smart Moving sets
            // srcArm's own rotation point to -5.0F / 5.0F directly (so px != 0) and ignores shoulder.
            // Only add parentShoulder offsets when srcArm is attached to shoulder (px == 0).
            if (parentShoulder != null && Math.abs(px) < 0.001F) {
                if (rotXField != null) rx += rotXField.getFloat(parentShoulder);
                if (rotYField != null) ry += rotYField.getFloat(parentShoulder);
                if (rotZField != null) rz += rotZField.getFloat(parentShoulder);

                if (pointXField != null) px += pointXField.getFloat(parentShoulder);
                if (pointYField != null) py += pointYField.getFloat(parentShoulder);
                if (pointZField != null) pz += pointZField.getFloat(parentShoulder);
            }

            if (rotXField != null) rotXField.setFloat(dstArm, rx);
            if (rotYField != null) rotYField.setFloat(dstArm, ry);
            if (rotZField != null) rotZField.setFloat(dstArm, rz);

            if (pointXField != null) pointXField.setFloat(dstArm, px);
            if (pointYField != null) pointYField.setFloat(dstArm, py);
            if (pointZField != null) pointZField.setFloat(dstArm, pz);
        } catch (Throwable ignored) {}
    }

    private static void syncRenderer(Object src, Object dst) {
        if (src == null || dst == null) return;
        try {
            if (rotXField != null) rotXField.setFloat(dst, rotXField.getFloat(src));
            if (rotYField != null) rotYField.setFloat(dst, rotYField.getFloat(src));
            if (rotZField != null) rotZField.setFloat(dst, rotZField.getFloat(src));
            if (pointXField != null) pointXField.setFloat(dst, pointXField.getFloat(src));
            if (pointYField != null) pointYField.setFloat(dst, pointYField.getFloat(src));
            if (pointZField != null) pointZField.setFloat(dst, pointZField.getFloat(src));
        } catch (Throwable ignored) {}
    }

    private static void syncWings(Object render, Object bipedBody) {
        if (render == null || bipedBody == null || modelWingsField == null) return;
        try {
            Object modelWings = modelWingsField.get(render);
            if (modelWings == null) return;

            Object left = wingLeftField != null ? wingLeftField.get(modelWings) : null;
            Object right = wingRightField != null ? wingRightField.get(modelWings) : null;
            if (left == null && right == null) return;

            float bodyRotX = rotXField != null ? rotXField.getFloat(bipedBody) : 0.0F;
            float bodyPointX = pointXField != null ? pointXField.getFloat(bipedBody) : 0.0F;
            float bodyPointY = pointYField != null ? pointYField.getFloat(bipedBody) : 0.0F;
            float bodyPointZ = pointZField != null ? pointZField.getFloat(bipedBody) : 0.0F;

            // Wing hinge default offset from body origin:
            // left: (0.5F, 5.0F, 2.625F), right: (-0.5F, 5.0F, 2.625F)
            float cosX = (float) Math.cos(bodyRotX);
            float sinX = (float) Math.sin(bodyRotX);

            float y0 = 5.0F;
            float z0 = 2.625F;

            float rotY = y0 * cosX - z0 * sinX;
            float rotZ = y0 * sinX + z0 * cosX;

            if (left != null) {
                if (rotXField != null) rotXField.setFloat(left, bodyRotX);
                if (pointXField != null) pointXField.setFloat(left, bodyPointX + 0.5F);
                if (pointYField != null) pointYField.setFloat(left, bodyPointY + rotY);
                if (pointZField != null) pointZField.setFloat(left, bodyPointZ + rotZ);
            }
            if (right != null) {
                if (rotXField != null) rotXField.setFloat(right, bodyRotX);
                if (pointXField != null) pointXField.setFloat(right, bodyPointX - 0.5F);
                if (pointYField != null) pointYField.setFloat(right, bodyPointY + rotY);
                if (pointZField != null) pointZField.setFloat(right, bodyPointZ + rotZ);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Directly applies OpenGL body rotation for Aether accessories (gloves, wings, shields, capes).
     * In inventory GUI preview: applies standard GUI preview rotation (faces forward).
     * In-game with Smart Moving: matches Smart Moving's bipedOuter interpolated body rotation.
     * In-game vanilla: applies angle-wrapped corpse rotation.
     */
    public static void rotateCorpseDirect(Object render, Object entity, float handleRotation, float bodyYaw, float partialTicks) {
        // 1. Inventory GUI preview check: always face forward
        if (isGuiPreview(partialTicks)) {
            GL11.glRotatef(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
            return;
        }

        // 2. Smart Moving in-game check: use Smart Moving's interpolated body rotation
        Object smartModel = getSmartModelBase(render);
        if (smartModel != null) {
            try {
                if (getOuterMethod != null) {
                    Object outer = getOuterMethod.invoke(smartModel);
                    if (outer != null && rotYField != null) {
                        float ry = rotYField.getFloat(outer) * 57.295776F;
                        GL11.glRotatef(180.0F - ry, 0.0F, 1.0F, 0.0F);
                        logDebugRotate("Smart Moving ry=" + ry + " (vanilla bodyYaw was " + bodyYaw + ")");
                        return;
                    } else {
                        logDebugRotate("Smart Moving outer=" + outer + ", rotYField=" + rotYField);
                    }
                } else {
                    logDebugRotate("Smart Moving getOuterMethod is null");
                }
            } catch (Throwable t) {
                logDebugRotate("Smart error: " + t);
            }
        }

        // 3. Fallback: Vanilla corpse rotation with proper angle wrapping
        if (entity != null && renderYawOffsetField != null && prevRenderYawOffsetField != null) {
            try {
                float curr = renderYawOffsetField.getFloat(entity);
                float prev = prevRenderYawOffsetField.getFloat(entity);
                float diff = curr - prev;
                while (diff < -180.0F) diff += 360.0F;
                while (diff >= 180.0F) diff -= 360.0F;
                bodyYaw = prev + diff * partialTicks;
            } catch (Throwable ignored) {}
        }
        GL11.glRotatef(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
        logDebugRotate("Fallback bodyYaw=" + bodyYaw);
    }

    public static void disableLightmap() {
        try {
            if (setActiveTextureMethod != null) {
                setActiveTextureMethod.invoke(null, lightmapTexUnit);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                setActiveTextureMethod.invoke(null, defaultTexUnit);
            }
        } catch (Throwable ignored) {}
    }

    public static void enableLightmap() {
        try {
            if (setActiveTextureMethod != null) {
                setActiveTextureMethod.invoke(null, lightmapTexUnit);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                setActiveTextureMethod.invoke(null, defaultTexUnit);
            }
        } catch (Throwable ignored) {}
    }

    public static void setLightmapCoords(float s, float t) {
        try {
            if (setLightmapCoordsMethod != null) {
                setLightmapCoordsMethod.invoke(null, lightmapTexUnit, s, t);
            }
        } catch (Throwable ignored) {}
    }

    public static void setupAccessoryLighting(Object entity, float partialTicks) {
        boolean gui = isGuiPreview(partialTicks);
        if (gui) {
            // In GUI inventory:
            // RenderLiving re-enables Texture Unit 1 (lightmap) right before exiting.
            // GUI inventory preview does not use lightmaps (it uses standard item lighting Light 0/1).
            // When Texture Unit 1 is enabled in GUI without coordinates, OpenGL samples (0,0) (pure black),
            // causing all accessory models (gloves, cape, etc.) to render completely unlit/black!
            disableLightmap();
        } else {
            // In-game world: Ensure lightmap texture unit is enabled and set coordinates from entity brightness
            enableLightmap();
            int brightness = 15728880;
            try {
                if (getBrightnessForRenderMethod != null && entity != null) {
                    brightness = ((Number) getBrightnessForRenderMethod.invoke(entity, partialTicks)).intValue();
                }
                if (isBurningMethod != null && entity != null && ((Boolean) isBurningMethod.invoke(entity))) {
                    brightness = 15728880;
                }
            } catch (Throwable ignored) {}
            int j = brightness % 65536;
            int k = brightness / 65536;
            setLightmapCoords((float) j / 1.0F, (float) k / 1.0F);
        }

        // Standard lighting & material properties for accessories
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GL11.glEnable(32826); // GL_RESCALE_NORMAL
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
    }

    /**
     * Called right after modelMisc.setRotationAngles(...) in RenderPlayerBaseAether.doRenderMisc.
     * Synchronizes modelMisc's right arm, left arm, and body with the Smart Moving player model,
     * and sets up the correct lighting state for accessories (handling GUI inventory preview vs in-game).
     */
    public static void beforeRenderAccessories(Object render, Object entity, float partialTicks) {
        // 1. Synchronize arm and body rotations/positions
        syncModel(render);

        // 2. Setup OpenGL lighting state
        setupAccessoryLighting(entity, partialTicks);
    }

    /**
     * Called before renderCape in RenderPlayerBaseAether.a(sq, float).
     */
    public static void beforeRenderCape(Object entity, float partialTicks) {
        setupAccessoryLighting(entity, partialTicks);
    }

    /**
     * Called after renderCape in RenderPlayerBaseAether.a(sq, float).
     */
    public static void afterRenderCape(float partialTicks) {
        GL11.glDepthMask(true);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (isGuiPreview(partialTicks)) {
            disableLightmap();
        } else {
            enableLightmap();
        }
    }

    /**
     * Called immediately after PlayerCoreRender.a(texture).
     * Ensures color is reset to pure white before each accessory (and enables lighting/materials/depth mask).
     */
    public static void prepareAccessoryColor() {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GL11.glEnable(32826); // GL_RESCALE_NORMAL
        GL11.glDepthMask(true);
    }

    /**
     * Called at the end of RenderPlayerBaseAether.doRenderMisc.
     * Leaves OpenGL lightmap state clean according to preview mode.
     */
    public static void afterRenderAccessories(float partialTicks) {
        GL11.glDepthMask(true);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (isGuiPreview(partialTicks)) {
            disableLightmap();
        } else {
            enableLightmap();
        }
    }
    /**
     * Determines whether accessory slot background icons should be displayed.
     * Only displays icons when the active GUI screen is the Aether inventory (GuiInventoryAether).
     * Suppresses icons in all other screens (such as Creative Survival Inventory).
     */
    public static boolean shouldShowAccessoryIcons() {
        try {
            return isAetherGuiOpen();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Maps Aether accessories (such as pendants) to their authentic vibrant material colors.
     * Pendants in Aether II share a greyscale texture template (/armor/Accessories.png) and
     * require material color tinting to avoid rendering as plain grey.
     */
    public static int getAccessoryColor(Object accessory, int defaultColor) {
        if (accessory == null) return defaultColor;
        try {
            if (accessory == net.aetherteam.aether.items.AetherItems.GoldenPendant) {
                return 0xFFD700; // Gold
            } else if (accessory == net.aetherteam.aether.items.AetherItems.ZanitePendant) {
                return 0x9933FF; // Zanite purple
            } else if (accessory == net.aetherteam.aether.items.AetherItems.IcePendant) {
                return 0x66CCFF; // Ice cyan
            } else if (accessory == net.aetherteam.aether.items.AetherItems.SwettyPendant) {
                return 0x3388FF; // Swetty blue
            } else if (accessory == net.aetherteam.aether.items.AetherItems.IronPendant) {
                return 0xD8D8D8; // Silver / Iron
            }
        } catch (Throwable ignored) {}
        return defaultColor;
    }

    /**
     * Applies missing player body rotation to the standalone modelCape in RenderPlayerBaseAether.renderCape.
     * With Smart Moving, body yaw is applied inside the model hierarchy (bipedOuter) rather than the GL matrix,
     * leaving the cape facing North (180 deg) permanently unless rotated here.
     */
    public static void applyCapeRotation(Object render, Object entity, float partialTicks) {
        if (isGuiPreview(partialTicks)) {
            // In GUI preview, rotateCorpse already applied body yaw to the matrix
            return;
        }

        Object smartModel = getSmartModelBase(render);
        if (smartModel != null) {
            try {
                if (getOuterMethod != null) {
                    Object outer = getOuterMethod.invoke(smartModel);
                    if (outer != null) {
                        float ry = rotYField != null ? rotYField.getFloat(outer) * 57.295776F : 0.0F;
                        float rx = rotXField != null ? rotXField.getFloat(outer) * 57.295776F : 0.0F;
                        float rz = rotZField != null ? rotZField.getFloat(outer) * 57.295776F : 0.0F;
                        float px = pointXField != null ? pointXField.getFloat(outer) : 0.0F;
                        float py = pointYField != null ? pointYField.getFloat(outer) : 0.0F;
                        float pz = pointZField != null ? pointZField.getFloat(outer) : 0.0F;

                        // Apply crawl/dive translation offsets if present
                        if (Math.abs(px) > 0.001F || Math.abs(py) > 0.001F || Math.abs(pz) > 0.001F) {
                            GL11.glTranslatef(px * 0.0625F, py * 0.0625F, pz * 0.0625F);
                        }
                        // Match Smart Moving ModelRotationRenderer XYZ order (glRotate Z, then Y, then X)
                        // Apply body roll
                        if (Math.abs(rz) > 0.001F) {
                            GL11.glRotatef(rz, 0.0F, 0.0F, 1.0F);
                        }
                        // Apply body yaw (+ry to match player facing direction)
                        if (Math.abs(ry) > 0.001F) {
                            GL11.glRotatef(ry, 0.0F, 1.0F, 0.0F);
                        }
                        // Apply body crawl pitch
                        if (Math.abs(rx) > 0.001F) {
                            GL11.glRotatef(rx, 1.0F, 0.0F, 0.0F);
                        }
                    }
                }
            } catch (Throwable t) {
                System.out.println("[AetherClientHelper] applyCapeRotation error: " + t);
            }
        }
    }
}

