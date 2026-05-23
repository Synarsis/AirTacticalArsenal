package com.synarsis.airtacticalarsenal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@OnlyIn(Dist.CLIENT)
public class ClientWaypointManager {
    private static final String FILE_NAME = "ata_waypoints.properties";

    private static final Map<SystemType, Map<String, SavedRoute>> savedRoutes = new LinkedHashMap<>();

    static {
        for (SystemType type : SystemType.values()) {
            savedRoutes.put(type, new LinkedHashMap<>());
        }
    }

    public enum SystemType {
        SHAHED, ISKANDER
    }

    public static class SavedRoute {
        public String name;
        public List<BlockPos> points;

        public SavedRoute(String name, List<BlockPos> points) {
            this.name = name;
            this.points = new ArrayList<>(points);
        }
    }

    private static File getConfigFile() {
        File configDir = new File(Minecraft.getInstance().gameDirectory, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, FILE_NAME);
    }

    public static void load() {
        for (Map<String, SavedRoute> map : savedRoutes.values()) {
            map.clear();
        }

        File file = getConfigFile();
        if (!file.exists())
            return;

        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);

            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("route.") && key.endsWith(".name")) {
                    String[] parts = key.split("\\.");
                    if (parts.length >= 4) { 
                        String typeStr = parts[1].toUpperCase();
                        SystemType type;
                        try {
                            type = SystemType.valueOf(typeStr);
                        } catch (IllegalArgumentException e) {
                            continue; 
                        }

                        StringBuilder idBuilder = new StringBuilder();
                        for (int i = 2; i < parts.length - 1; i++) {
                            if (i > 2)
                                idBuilder.append(".");
                            idBuilder.append(parts[i]);
                        }
                        String id = idBuilder.toString();

                        String name = props.getProperty(key);
                        String pointsStr = props.getProperty("route." + typeStr.toLowerCase() + "." + id + ".points",
                                "");

                        List<BlockPos> points = new ArrayList<>();
                        if (!pointsStr.isEmpty()) {
                            String[] pts = pointsStr.split(";");
                            for (String pt : pts) {
                                if (pt.trim().isEmpty())
                                    continue;
                                String[] coords = pt.split(",");
                                if (coords.length >= 3) {
                                    try {
                                        int x = Integer.parseInt(coords[0]);
                                        int y = Integer.parseInt(coords[1]);
                                        int z = Integer.parseInt(coords[2]);
                                        points.add(new BlockPos(x, y, z));
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            }
                        }
                        if (!points.isEmpty()) {
                            savedRoutes.get(type).put(name, new SavedRoute(name, points));
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveRoute(SystemType type, String name, List<BlockPos> points) {
        if (name == null || name.trim().isEmpty() || points == null || points.isEmpty()) {
            return;
        }
        savedRoutes.get(type).put(name, new SavedRoute(name, points));
        saveToFile();
    }

    public static void deleteRoute(SystemType type, String name) {
        if (savedRoutes.get(type).remove(name) != null) {
            saveToFile();
        }
    }

    private static void saveToFile() {
        File file = getConfigFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            Properties props = new Properties();

            for (SystemType type : SystemType.values()) {
                int i = 0;
                String typeStr = type.name().toLowerCase();
                for (SavedRoute route : savedRoutes.get(type).values()) {
                    String id = "id" + i++;
                    String prefix = "route." + typeStr + "." + id;
                    props.setProperty(prefix + ".name", route.name);

                    StringBuilder sb = new StringBuilder();
                    for (BlockPos pt : route.points) {
                        sb.append(pt.getX()).append(",").append(pt.getY()).append(",").append(pt.getZ()).append(";");
                    }
                    props.setProperty(prefix + ".points", sb.toString());
                }
            }

            props.store(fos, "Air Tactical Arsenal Saved Routes and Waypoints");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<SavedRoute> getRoutes(SystemType type) {
        return new ArrayList<>(savedRoutes.get(type).values());
    }

    public static SavedRoute getRoute(SystemType type, String name) {
        return savedRoutes.get(type).get(name);
    }

    public static boolean hasSaved(SystemType type) {
        return !savedRoutes.get(type).isEmpty();
    }
}
