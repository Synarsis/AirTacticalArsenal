package com.synarsis.airtacticalarsenal.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class SuperbWarfareCompat {

    private static boolean swLoaded = false;
    private static boolean checkedSw = false;
    private static boolean vvpLoaded = false;
    private static boolean checkedVvp = false;

    private static final String SW_PKG  = "com.atsuishio.superbwarfare";
    private static final String VVP_PKG = "tech.vvp.vvp";

    private static final String[] HELI_KEYWORDS  = {"helicopter", "heli", "copter", "rotor"};
    private static final String[] PLANE_KEYWORDS = {"airplane", "aircraft", "fixedwing", "plane", "jet", "bomber", "fighter"};
    private static final String[] DRONE_KEYWORDS = {"drone", "uav", "fpv", "quadrotor"};

    private static final String[] HELI_NAMES  = {
        "ah6", "ah64", "ah1", "mi8", "mi17", "mi24", "mi26", "mi28",
        "uh60", "ch47", "cobra", "nh90", "ka52", "ka50", "hind", "havoc",
        "apache", "blackhawk", "chinook"
    };
    private static final String[] PLANE_NAMES = {
        "a10", "ju87", "tom6", "kv16", "f16", "mig", "su27", "su25",
        "b52", "warthog", "stuka", "thunderbolt"
    };
    private static final String[] DRONE_NAMES = {
        "drone", "uav", "fpv"
    };

    public static final byte TYPE_SW_HELICOPTER = 3;
    public static final byte TYPE_SW_PLANE      = 4;
    public static final byte TYPE_SW_DRONE      = 5;

    public static boolean isLoaded() {
        if (!checkedSw) { swLoaded = ModList.get().isLoaded("superbwarfare"); checkedSw = true; }
        return swLoaded;
    }

    public static boolean isVvpLoaded() {
        if (!checkedVvp) { vvpLoaded = ModList.get().isLoaded("vvp"); checkedVvp = true; }
        return vvpLoaded;
    }

    public static byte getRadarType(Entity entity) {
        if (!isLoaded() && !isVvpLoaded()) return -1;

        String pkg = entity.getClass().getName();
        boolean isSW  = isLoaded()    && pkg.startsWith(SW_PKG);
        boolean isVVP = isVvpLoaded() && pkg.startsWith(VVP_PKG);
        if (!isSW && !isVVP) return -1;

        byte byHierarchy = classifyByHierarchy(entity);
        if (byHierarchy >= 0) return byHierarchy;

        ResourceLocation regKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (regKey != null) {
            byte byReg = classifyByRegistryPath(regKey.getPath());
            if (byReg >= 0) return byReg;
        }

        return classifyByExactName(pkg);
    }

    private static byte classifyByHierarchy(Entity entity) {
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            String sn = clazz.getSimpleName().toLowerCase();
            for (String kw : HELI_KEYWORDS)  if (sn.contains(kw)) return TYPE_SW_HELICOPTER;
            for (String kw : PLANE_KEYWORDS) if (sn.contains(kw)) return TYPE_SW_PLANE;
            for (String kw : DRONE_KEYWORDS) if (sn.contains(kw)) return TYPE_SW_DRONE;
            clazz = clazz.getSuperclass();
        }
        return -1;
    }

    private static byte classifyByRegistryPath(String path) {
        String p = path.toLowerCase();
        for (String n : HELI_NAMES)  if (p.contains(n)) return TYPE_SW_HELICOPTER;
        for (String n : PLANE_NAMES) if (p.contains(n)) return TYPE_SW_PLANE;
        for (String n : DRONE_NAMES) if (p.contains(n)) return TYPE_SW_DRONE;
        return -1;
    }

    private static byte classifyByExactName(String className) {

        if (className.equals(SW_PKG + ".entity.vehicle.Ah6Entity")  ||
            className.equals(SW_PKG + ".entity.vehicle.Mi28Entity")) return TYPE_SW_HELICOPTER;

        if (className.equals(SW_PKG + ".entity.vehicle.A10Entity")  ||
            className.equals(SW_PKG + ".entity.vehicle.Ju87Entity") ||
            className.equals(SW_PKG + ".entity.vehicle.Tom6Entity") ||
            className.equals(SW_PKG + ".entity.vehicle.Kv16Entity")) return TYPE_SW_PLANE;

        if (className.equals(SW_PKG + ".entity.vehicle.DroneEntity")) return TYPE_SW_DRONE;

        if (className.equals(VVP_PKG + ".entity.vehicle.Ah64Entity") ||
            className.equals(VVP_PKG + ".entity.vehicle.Mi28Entity") ||
            className.equals(VVP_PKG + ".entity.vehicle.Mi24Entity") ||
            className.equals(VVP_PKG + ".entity.vehicle.Mi8Entity")  ||
            className.equals(VVP_PKG + ".entity.vehicle.CobraEntity")||
            className.equals(VVP_PKG + ".entity.vehicle.Nh90Entity")) return TYPE_SW_HELICOPTER;
        return -1;
    }

    public static boolean isVehicle(Entity entity) {
        if (!isLoaded() && !isVvpLoaded()) return false;
        String pkg = entity.getClass().getName();
        boolean isSW  = isLoaded()    && pkg.startsWith(SW_PKG);
        boolean isVVP = isVvpLoaded() && pkg.startsWith(VVP_PKG);
        if (!isSW && !isVVP) return false;

        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            if (clazz.getSimpleName().toLowerCase().contains("vehicle")) return true;
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    public static String getTypeName(byte type) {
        return switch (type) {
            case TYPE_SW_HELICOPTER -> "ВЕРТОЛЁТ";
            case TYPE_SW_PLANE      -> "САМОЛЁТ";
            case TYPE_SW_DRONE      -> "БПЛА";
            default -> "?";
        };
    }
}
