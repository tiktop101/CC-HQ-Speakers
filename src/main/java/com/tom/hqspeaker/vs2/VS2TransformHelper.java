package com.tom.hqspeaker.vs2;

import com.tom.hqspeaker.HQSpeakerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4dc;

public class VS2TransformHelper {

    private static Boolean vs2Available = null;
    private static boolean checkedForVS2 = false;

    public static boolean isVS2Loaded() {
        if (checkedForVS2) {
            return vs2Available != null && vs2Available;
        }
        checkedForVS2 = true;
        try {
            Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            vs2Available = true;
            HQSpeakerMod.log("Valkyrien Skies 2 detected - VS2 integration enabled");
            return true;
        } catch (ClassNotFoundException e) {
            vs2Available = false;
            HQSpeakerMod.log("Valkyrien Skies 2 not detected - using standard coordinate system");
            return false;
        } catch (Exception e) {
            vs2Available = false;
            HQSpeakerMod.warn("Error checking for Valkyrien Skies 2: " + e.getMessage());
            return false;
        }
    }

    public static Object getShipManagingBlock(Level level, BlockPos pos) {
        if (!isVS2Loaded()) return null;
        try {
            Class<?> vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            java.lang.reflect.Method getShipManagingPosMethod = vsGameUtilsClass.getMethod(
                "getShipManagingPos", Level.class, BlockPos.class);
            return getShipManagingPosMethod.invoke(null, level, pos);
        } catch (ClassNotFoundException e) {
            HQSpeakerMod.warn("VS2 class not found despite detection: " + e.getMessage());
            vs2Available = false;
            return null;
        } catch (NoSuchMethodException e) {
            HQSpeakerMod.error("VS2 API mismatch - getShipManagingPos method not found: " + e.getMessage());
            vs2Available = false;
            return null;
        } catch (Exception e) {
            HQSpeakerMod.error("Error getting ship from VS2: " + e.getMessage());
            return null;
        }
    }

    
    public static Matrix4dc getShipToWorldMatrix(Object ship) {
        if (ship == null) return null;
        try {
            
            
            try {
                java.lang.reflect.Method getRenderTransform =
                    ship.getClass().getMethod("getRenderTransform");
                Object renderTransform = getRenderTransform.invoke(ship);
                if (renderTransform != null) {
                    try {
                        java.lang.reflect.Method m =
                            renderTransform.getClass().getMethod("getShipToWorldMatrix");
                        Matrix4dc result = (Matrix4dc) m.invoke(renderTransform);
                        if (result != null) return result;
                    } catch (NoSuchMethodException ignored) {}
                    
                    try {
                        java.lang.reflect.Method m =
                            renderTransform.getClass().getMethod("getShipToWorld");
                        Matrix4dc result = (Matrix4dc) m.invoke(renderTransform);
                        if (result != null) return result;
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (NoSuchMethodException ignored) {}

            
            try {
                java.lang.reflect.Method getTransform =
                    ship.getClass().getMethod("getTransform");
                Object transform = getTransform.invoke(ship);
                if (transform != null) {
                    
                    try {
                        java.lang.reflect.Method m =
                            transform.getClass().getMethod("getShipToWorld");
                        Matrix4dc result = (Matrix4dc) m.invoke(transform);
                        if (result != null) return result;
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        java.lang.reflect.Method m =
                            transform.getClass().getMethod("getShipToWorldMatrix");
                        Matrix4dc result = (Matrix4dc) m.invoke(transform);
                        if (result != null) return result;
                    } catch (NoSuchMethodException ignored) {}
                    
                    for (java.lang.reflect.Method m : transform.getClass().getMethods()) {
                        if (Matrix4dc.class.isAssignableFrom(m.getReturnType())
                                && m.getParameterCount() == 0
                                && m.getName().toLowerCase().contains("shiptoworld")) {
                            try {
                                Matrix4dc result = (Matrix4dc) m.invoke(transform);
                                if (result != null) {
                                    HQSpeakerMod.log("VS2: found ship-to-world via scan: "
                                        + m.getName());
                                    return result;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {}

            HQSpeakerMod.error("VS2: could not find any ship-to-world matrix method on "
                + ship.getClass().getName());
            return null;

        } catch (Exception e) {
            HQSpeakerMod.error("VS2: getShipToWorldMatrix failed: " + e.getMessage());
            return null;
        }
    }

    public static void resetCache() {
        vs2Available = null;
        checkedForVS2 = false;
    }
}
