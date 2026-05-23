package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.radar.RouteTerrainRenderer;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.RequestRouteMapPacket;
import com.synarsis.airtacticalarsenal.network.SaveShahedRoutePacket;
import com.synarsis.airtacticalarsenal.radar.ServerRouteMapScanner;
import com.synarsis.airtacticalarsenal.util.LauncherZoneValidator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ShahedRouteScreen extends Screen {

    private static final int GUI_WIDTH = 520;
    private static final int GUI_HEIGHT = 460;
    private static final int PROFILE_HEIGHT = 100;

    private static final int MAP_SIZE = 300;
    private static final int MAP_MARGIN = 15;

    private static final int BG_COLOR = 0xE0101010;
    private static final int BORDER_COLOR = 0xFF006400;
    private static final int LAUNCHER_COLOR = 0xFFFFFF00;
    private static final int WAYPOINT_COLOR = 0xFF00FF00;
    private static final int TARGET_COLOR = 0xFFFF3333;
    private static final int ROUTE_LINE_COLOR = 0xAA00FF00;
    private static final int ZONE_COLOR = 0x4000FF00;
    private static final int PLAYER_COLOR = 0xFF4488FF;

    private double scale = 10.0;
    private static final double MIN_SCALE = 2.0;
    private static final double MAX_SCALE = 80.0;

    private double viewCenterX;
    private double viewCenterZ;
    private boolean isDragging = false;
    private double dragStartX, dragStartZ;

    private final List<BlockPos> launcherPositions;
    private final List<Float> launcherYaws;
    private final List<Boolean> hasDroneList;
    private final List<List<BlockPos>> allRoutes;
    private int currentEditIndex = 0;
    private final Screen parentScreen;
    private final Consumer<List<List<BlockPos>>> onSaveCallback;

    private static final int MIN_FIRST_POINT_DISTANCE = 600;
    private static final int MAX_FIRST_POINT_DISTANCE = 700;
    private static final int MIN_BETWEEN_POINTS_DISTANCE = 250;
    private static final int MIN_TARGET_DISTANCE = 1500;
    private static final int MAX_FLIGHT_DISTANCE = 12000;

    private final java.util.Map<Integer, Integer> segmentMaxHeights = new java.util.HashMap<>();

    private static final int[] MULTI_COLORS = {
            0xFFFFFF00, 0xFF00FFFF, 0xFFFF00FF, 0xFFFF8800,
            0xFF88FF00, 0xFF0088FF, 0xFFFF0088, 0xFF00FF88,
    };
    private static final int[] ROUTE_COLORS = {
            0xAA00FF00, 0xAA00CCFF, 0xAAFF00FF, 0xAAFF8800,
            0xAA88FF00, 0xAA0088FF, 0xAAFF0088, 0xAA00FF88,
    };

    private static final int MAX_WAYPOINTS = 20;

    private Button saveButton;
    private Button clearButton;
    private Button backButton;
    private Button prevLauncherBtn;
    private Button nextLauncherBtn;
    private Button applyAllBtn;
    private Button coordAddBtn;
    private EditBox coordXField;
    private EditBox coordZField;

    private EditBox routeNameField;
    private Button saveRouteBtn;
    private Button deleteRouteBtn;
    private int selectedSavedRouteIndex = -1;

    private final Button[] routeButtons = new Button[5];
    private int scrollOffset = 0;

    private List<com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SavedRoute> cachedSavedRoutes = new ArrayList<>();

    private final BlockPos mapCenter;

    private String statusMessage = "";
    private int statusTicks = 0;

    private RouteTerrainRenderer terrainRenderer;
    private int mapRequestTicks = 0;
    private static final int MAP_REQUEST_INTERVAL = 10;

    private int clipMinX, clipMinY, clipMaxX, clipMaxY;

    private int draggingWaypointIndex = -1;
    private int profileX, profileY, profileW, profileH;
    private static final int DEFAULT_WAYPOINT_ALT = 165;

    public ShahedRouteScreen(Screen parent, List<BlockPos> positions, List<Float> yaws,
            List<Boolean> drones, List<List<BlockPos>> routes,
            Consumer<List<List<BlockPos>>> onSaveCallback) {
        super(Component.literal(""));
        this.parentScreen = parent;
        this.onSaveCallback = onSaveCallback;
        this.launcherPositions = new ArrayList<>(positions);
        this.launcherYaws = new ArrayList<>(yaws);
        this.hasDroneList = new ArrayList<>(drones);
        this.allRoutes = new ArrayList<>();
        for (List<BlockPos> r : routes) {
            this.allRoutes.add(new ArrayList<>(r));
        }
        BlockPos firstPos = positions.get(0);
        this.viewCenterX = firstPos.getX();
        this.viewCenterZ = firstPos.getZ();

        if (RouteTerrainRenderer.hasCachedDataNear(firstPos.getX(), firstPos.getZ())) {
            this.mapCenter = new BlockPos(
                    RouteTerrainRenderer.getCachedCenterXNear(firstPos.getX(), firstPos.getZ()), 0,
                    RouteTerrainRenderer.getCachedCenterZNear(firstPos.getX(), firstPos.getZ()));
        } else {
            this.mapCenter = firstPos;
        }

        for (int i = 0; i < allRoutes.size(); i++) {
            autoPlaceFirstWaypoint(i);
        }
    }

    private void autoPlaceFirstWaypoint(int routeIndex) {
        if (!allRoutes.get(routeIndex).isEmpty())
            return;
        BlockPos lPos = launcherPositions.get(routeIndex);
        float yaw = launcherYaws.get(routeIndex);
        double rad = Math.toRadians(yaw);
        int dist = (MIN_FIRST_POINT_DISTANCE + MAX_FIRST_POINT_DISTANCE) / 2;
        int wx = lPos.getX() + (int) (-Math.sin(rad) * dist);
        int wz = lPos.getZ() + (int) (Math.cos(rad) * dist);
        allRoutes.get(routeIndex).add(new BlockPos(wx, DEFAULT_WAYPOINT_ALT, wz));
    }

    private List<BlockPos> currentRoute() {
        return allRoutes.get(currentEditIndex);
    }

    private BlockPos currentLauncherPos() {
        return launcherPositions.get(currentEditIndex);
    }

    private float currentLauncherYaw() {
        return launcherYaws.get(currentEditIndex);
    }

    @Override
    protected void init() {
        super.init();

        com.synarsis.airtacticalarsenal.client.ClientWaypointManager.load();
        cachedSavedRoutes = com.synarsis.airtacticalarsenal.client.ClientWaypointManager
                .getRoutes(com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SystemType.SHAHED);
        if (!cachedSavedRoutes.isEmpty()) {
            selectedSavedRouteIndex = 0;
        }

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int rightPanel = guiLeft + MAP_MARGIN + MAP_SIZE + 10;

        clipMinX = guiLeft + MAP_MARGIN;
        clipMinY = guiTop + MAP_MARGIN + 20;
        clipMaxX = clipMinX + MAP_SIZE;
        clipMaxY = clipMinY + MAP_SIZE;

        if (terrainRenderer == null) {
            terrainRenderer = new RouteTerrainRenderer(mapCenter.getX(), mapCenter.getZ());
            if (!terrainRenderer.hasCacheData()) {
                NetworkHandler.sendToServer(new RequestRouteMapPacket(launcherPositions.get(0)));
            }
        }

        if (launcherPositions.size() > 1) {
            this.prevLauncherBtn = Button.builder(
                    Component.literal("\u25C0"),
                    button -> switchLauncher(-1))
                    .bounds(rightPanel, guiTop + 24, 20, 16)
                    .build();
            this.addWidget(this.prevLauncherBtn);

            this.nextLauncherBtn = Button.builder(
                    Component.literal("\u25B6"),
                    button -> switchLauncher(1))
                    .bounds(rightPanel + 110, guiTop + 24, 20, 16)
                    .build();
            this.addWidget(this.nextLauncherBtn);

            this.applyAllBtn = Button.builder(
                    Component.literal("ВСЕМ ТАКОЙ"),
                    button -> applyToAll())
                    .bounds(rightPanel, guiTop + GUI_HEIGHT - 85, GUI_WIDTH - MAP_MARGIN - MAP_SIZE - 10 - MAP_MARGIN,
                            20)
                    .build();
            this.addWidget(this.applyAllBtn);
        }

        int panelW = GUI_WIDTH - MAP_MARGIN - MAP_SIZE - 10 - MAP_MARGIN;
        int btnW = (panelW - 5) / 2;

        this.clearButton = Button.builder(
                Component.literal("\u041e\u0427\u0418\u0421\u0422\u0418\u0422\u042c"),
                button -> {
                    currentRoute().clear();
                    autoPlaceFirstWaypoint(currentEditIndex);
                })
                .bounds(rightPanel, guiTop + GUI_HEIGHT - 60, panelW, 20)
                .build();
        this.addWidget(this.clearButton);

        this.saveButton = Button.builder(
                Component.literal("\u0421\u041e\u0425\u0420\u0410\u041d\u0418\u0422\u042c"),
                button -> onSaveClicked())
                .bounds(rightPanel, guiTop + GUI_HEIGHT - 35, btnW, 20)
                .build();
        this.addWidget(this.saveButton);

        this.backButton = Button.builder(
                Component.literal("\u041d\u0410\u0417\u0410\u0414"),
                button -> Minecraft.getInstance().setScreen(parentScreen))
                .bounds(rightPanel + btnW + 5, guiTop + GUI_HEIGHT - 35, btnW, 20)
                .build();
        this.addWidget(this.backButton);

        int coordY = guiTop + GUI_HEIGHT - 105;
        int fieldW = (panelW - 30) / 2;
        this.coordXField = new EditBox(this.font, rightPanel, coordY, fieldW, 14, Component.literal("X"));
        this.coordXField.setMaxLength(8);
        this.coordXField.setHint(Component.literal("X"));
        this.addWidget(this.coordXField);

        this.coordZField = new EditBox(this.font, rightPanel + fieldW + 5, coordY, fieldW, 14, Component.literal("Z"));
        this.coordZField.setMaxLength(8);
        this.coordZField.setHint(Component.literal("Z"));
        this.addWidget(this.coordZField);

        this.coordAddBtn = Button.builder(
                Component.literal("+"),
                button -> addWaypointByCoords())
                .bounds(rightPanel + fieldW * 2 + 10, coordY, 20, 14)
                .build();
        this.addWidget(this.coordAddBtn);

        int listY = guiTop + 141; 

        for (int i = 0; i < 5; i++) {
            final int index = i;
            this.routeButtons[i] = Button.builder(Component.literal(""), b -> selectRoute(index))
                    .bounds(rightPanel, listY + i * 22, panelW, 20).build();
            this.addWidget(this.routeButtons[i]);
        }
        updateRouteButtons();

        int saveY = listY + 115;
        this.routeNameField = new EditBox(this.font, rightPanel, saveY, panelW, 14, Component.literal("Имя маршрута"));
        this.routeNameField.setMaxLength(20);
        this.addWidget(this.routeNameField);

        if (selectedSavedRouteIndex >= 0 && selectedSavedRouteIndex < cachedSavedRoutes.size()) {
            this.routeNameField.setValue(cachedSavedRoutes.get(selectedSavedRouteIndex).name);
        }

        btnW = (panelW - 5) / 2;
        this.saveRouteBtn = Button
                .builder(Component.literal("Сохранить"), button -> saveCurrentRouteToManager())
                .bounds(rightPanel, saveY + 18, btnW, 16).build();
        this.addWidget(this.saveRouteBtn);

        this.deleteRouteBtn = Button
                .builder(Component.literal("Удалить"), button -> deleteSelectedRouteFromManager())
                .bounds(rightPanel + btnW + 5, saveY + 18, btnW, 16).build();
        this.addWidget(this.deleteRouteBtn);

        int savedIdx = currentEditIndex;
        for (int ri = 0; ri < allRoutes.size(); ri++) {
            currentEditIndex = ri;
            for (int wi = 0; wi < allRoutes.get(ri).size(); wi++) {
                requestSegmentHeightForWaypoint(wi);
            }
        }
        currentEditIndex = savedIdx;
    }

    private void scroll(int delta) {
        scrollOffset += delta;
        updateRouteButtons();
    }

    private void updateRouteButtons() {
        int maxOffset = Math.max(0, cachedSavedRoutes.size() - 5);
        if (scrollOffset > maxOffset)
            scrollOffset = maxOffset;
        if (scrollOffset < 0)
            scrollOffset = 0;

        for (int i = 0; i < 5; i++) {
            int routeIndex = scrollOffset + i;
            if (routeIndex < cachedSavedRoutes.size()) {
                routeButtons[i].visible = true;
                String name = cachedSavedRoutes.get(routeIndex).name;
                if (routeIndex == selectedSavedRouteIndex) {
                    routeButtons[i]
                            .setMessage(Component.literal("> " + name + " <").withStyle(s -> s.withColor(0xFFFF55)));
                } else {
                    routeButtons[i].setMessage(Component.literal(name));
                }
            } else {
                routeButtons[i].visible = false;
            }
        }
    }

    private void selectRoute(int buttonIndex) {
        int routeIndex = scrollOffset + buttonIndex;
        if (routeIndex >= 0 && routeIndex < cachedSavedRoutes.size()) {
            selectedSavedRouteIndex = routeIndex;
            updateRouteButtons();
            loadSelectedRouteFromManager();
        }
    }

    private void saveCurrentRouteToManager() {
        String n = routeNameField.getValue().trim();
        if (n.isEmpty()) {
            setStatus("§cВведите имя для сохранения!");
            return;
        }
        if (currentRoute().size() < 2) {
            setStatus("§cМаршрут должен содержать минимум 2 точки!");
            return;
        }

        com.synarsis.airtacticalarsenal.client.ClientWaypointManager.saveRoute(
                com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SystemType.SHAHED, n,
                new java.util.ArrayList<>(currentRoute().subList(1, currentRoute().size())));
        cachedSavedRoutes = com.synarsis.airtacticalarsenal.client.ClientWaypointManager
                .getRoutes(com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SystemType.SHAHED);
        for (int i = 0; i < cachedSavedRoutes.size(); i++) {
            if (cachedSavedRoutes.get(i).name.equals(n)) {
                selectedSavedRouteIndex = i;
                break;
            }
        }
        updateRouteButtons();
        setStatus("§aМаршрут '" + n + "' сохранён локально");
    }

    private void loadSelectedRouteFromManager() {
        if (selectedSavedRouteIndex >= 0 && selectedSavedRouteIndex < cachedSavedRoutes.size()) {
            var saved = cachedSavedRoutes.get(selectedSavedRouteIndex);
            if (saved.points != null && !saved.points.isEmpty()) {
                currentRoute().clear();

                autoPlaceFirstWaypoint(currentEditIndex);

                for (int i = 0; i < saved.points.size(); i++) {
                    BlockPos pt = saved.points.get(i);
                    currentRoute().add(new BlockPos(pt.getX(), pt.getY(), pt.getZ()));
                }

                setStatus("§aМаршрут '" + saved.name + "' загружен");

                for (int wi = 0; wi < currentRoute().size(); wi++) {
                    requestSegmentHeightForWaypoint(wi);
                }
            }
        } else {
            setStatus("§cНет сохраненных маршрутов для загрузки!");
        }
    }

    private void deleteSelectedRouteFromManager() {
        if (selectedSavedRouteIndex >= 0 && selectedSavedRouteIndex < cachedSavedRoutes.size()) {
            String name = cachedSavedRoutes.get(selectedSavedRouteIndex).name;
            com.synarsis.airtacticalarsenal.client.ClientWaypointManager.deleteRoute(
                    com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SystemType.SHAHED, name);

            cachedSavedRoutes = com.synarsis.airtacticalarsenal.client.ClientWaypointManager.getRoutes(
                    com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SystemType.SHAHED);

            selectedSavedRouteIndex = -1;
            scrollOffset = 0;
            updateRouteButtons();

            this.routeNameField.setValue("");
            setStatus("§eМаршрут '" + name + "' удалён");
        }
    }

    private void addWaypointByCoords() {
        try {
            int wx = Integer.parseInt(coordXField.getValue().trim());
            int wz = Integer.parseInt(coordZField.getValue().trim());
            if (currentRoute().size() >= MAX_WAYPOINTS) {
                setStatus("§cМакс. " + MAX_WAYPOINTS + " точек!");
                return;
            }
            BlockPos lPos = currentLauncherPos();
            if (currentRoute().isEmpty()) {
                double distToLauncher = Math.sqrt(
                        Math.pow(wx - lPos.getX(), 2) + Math.pow(wz - lPos.getZ(), 2));
                if (distToLauncher < MIN_FIRST_POINT_DISTANCE) {
                    setStatus("§cМин. до первой точки: " + MIN_FIRST_POINT_DISTANCE + " бл!");
                    return;
                }
                if (distToLauncher > MAX_FIRST_POINT_DISTANCE) {
                    setStatus("§cМакс. до первой точки: " + MAX_FIRST_POINT_DISTANCE + " бл!");
                    return;
                }
            } else {
                BlockPos prevPoint = currentRoute().get(currentRoute().size() - 1);
                double distToPrev = Math.sqrt(
                        Math.pow(wx - prevPoint.getX(), 2) + Math.pow(wz - prevPoint.getZ(), 2));
                if (distToPrev < MIN_BETWEEN_POINTS_DISTANCE) {
                    setStatus("§cМин. между точками: " + MIN_BETWEEN_POINTS_DISTANCE + " бл!");
                    return;
                }
            }
            BlockPos newPoint = new BlockPos(wx, DEFAULT_WAYPOINT_ALT, wz);
            currentRoute().add(newPoint);
            int wpIdx = currentRoute().size() - 1;
            requestSegmentHeightForWaypoint(wpIdx);
            double totalDist = calculateTotalDistance();
            if (totalDist > MAX_FLIGHT_DISTANCE) {
                currentRoute().remove(currentRoute().size() - 1);
                setStatus("§cМаршрут превышает макс. дистанцию " + MAX_FLIGHT_DISTANCE + " бл!");
                return;
            }
            coordXField.setValue("");
            coordZField.setValue("");
            setStatus("§aТочка добавлена: [" + wx + ", " + wz + "]");
        } catch (NumberFormatException e) {
            setStatus("§cВведите числовые координаты X и Z!");
        }
    }

    private int worldToMapX(double worldX) {
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int mapCenterX = guiLeft + MAP_MARGIN + MAP_SIZE / 2;
        return mapCenterX + (int) ((worldX - viewCenterX) / scale);
    }

    private int worldToMapZ(double worldZ) {
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int mapCenterZ = guiTop + MAP_MARGIN + 20 + MAP_SIZE / 2;
        return mapCenterZ + (int) ((worldZ - viewCenterZ) / scale);
    }

    private int mapToWorldX(double screenX) {
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int mapCenterX = guiLeft + MAP_MARGIN + MAP_SIZE / 2;
        return (int) ((screenX - mapCenterX) * scale + viewCenterX);
    }

    private int mapToWorldZ(double screenZ) {
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int mapCenterZ = guiTop + MAP_MARGIN + 20 + MAP_SIZE / 2;
        return (int) ((screenZ - mapCenterZ) * scale + viewCenterZ);
    }

    private boolean isOnMap(double mouseX, double mouseY) {
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int mapX = guiLeft + MAP_MARGIN;
        int mapY = guiTop + MAP_MARGIN + 20;
        return mouseX >= mapX && mouseX < mapX + MAP_SIZE && mouseY >= mapY && mouseY < mapY + MAP_SIZE;
    }

    private boolean isOnProfile(double mx, double my) {
        return mx >= profileX && mx < profileX + profileW && my >= profileY && my < profileY + profileH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOnProfile(mouseX, mouseY) && !currentRoute().isEmpty()) {
            int wpIdx = findNearestWaypointOnProfile(mouseX);
            if (wpIdx >= 0) {
                draggingWaypointIndex = wpIdx;
                updateWaypointAltFromMouse(wpIdx, mouseY);
                return true;
            }
        }
        if (isOnMap(mouseX, mouseY)) {
            if (button == 0) {

                if (currentRoute().size() < MAX_WAYPOINTS) {
                    int wx = mapToWorldX(mouseX);
                    int wz = mapToWorldZ(mouseY);
                    BlockPos newPoint = new BlockPos(wx, DEFAULT_WAYPOINT_ALT, wz);
                    BlockPos lPos = currentLauncherPos();

                    if (currentRoute().isEmpty()) {
                        double distToLauncher = Math.sqrt(
                                Math.pow(wx - lPos.getX(), 2) + Math.pow(wz - lPos.getZ(), 2));
                        if (distToLauncher < MIN_FIRST_POINT_DISTANCE) {
                            setStatus("§cМин. расстояние до первой точки: " + MIN_FIRST_POINT_DISTANCE + " бл!");
                            return true;
                        }
                        if (distToLauncher > MAX_FIRST_POINT_DISTANCE) {
                            setStatus("§cМакс. расстояние до первой точки: " + MAX_FIRST_POINT_DISTANCE + " бл!");
                            return true;
                        }
                    } else {
                        BlockPos prevPoint = currentRoute().get(currentRoute().size() - 1);
                        double distToPrev = Math.sqrt(
                                Math.pow(wx - prevPoint.getX(), 2) + Math.pow(wz - prevPoint.getZ(), 2));
                        if (distToPrev < MIN_BETWEEN_POINTS_DISTANCE) {
                            setStatus("§cМин. расстояние между точками: " + MIN_BETWEEN_POINTS_DISTANCE + " бл!");
                            return true;
                        }
                    }

                    currentRoute().add(newPoint);
                    int wpIdx = currentRoute().size() - 1;

                    requestSegmentHeightForWaypoint(wpIdx);
                    double totalDist = calculateTotalDistance();
                    if (totalDist > MAX_FLIGHT_DISTANCE) {
                        currentRoute().remove(currentRoute().size() - 1);
                        setStatus("§cМаршрут превышает макс. дистанцию " + MAX_FLIGHT_DISTANCE + " бл!");
                    }
                    return true;
                } else {
                    setStatus("§cМакс. " + MAX_WAYPOINTS + " точек!");
                }
            } else if (button == 1) {

                if (currentRoute().size() > 1) {
                    currentRoute().remove(currentRoute().size() - 1);
                    return true;
                }
            } else if (button == 2) {

                isDragging = true;
                dragStartX = mouseX;
                dragStartZ = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingWaypointIndex >= 0) {
            draggingWaypointIndex = -1;
            return true;
        }
        if (button == 2 && isDragging) {
            isDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingWaypointIndex >= 0) {
            updateWaypointAltFromMouse(draggingWaypointIndex, mouseY);
            return true;
        }
        if (button == 2 && isDragging) {

            viewCenterX -= dragX * scale;
            viewCenterZ -= dragY * scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int mapX = guiLeft + MAP_MARGIN;
        int rightPanel = mapX + MAP_SIZE + 10;
        int panelW = GUI_WIDTH - MAP_MARGIN - MAP_SIZE - 10 - MAP_MARGIN;

        if (mouseX >= rightPanel && mouseX <= rightPanel + panelW && mouseY >= guiTop + GUI_HEIGHT - 332
                && mouseY <= guiTop + GUI_HEIGHT - 211) {
            if (delta > 0)
                scroll(-1);
            else if (delta < 0)
                scroll(1);
            return true;
        }

        if (!isOnMap(mouseX, mouseY))
            return super.mouseScrolled(mouseX, mouseY, delta);

        double factor = delta > 0 ? 0.8 : 1.25;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        return true;
    }

    private void switchLauncher(int delta) {
        currentEditIndex = (currentEditIndex + delta + launcherPositions.size()) % launcherPositions.size();
    }

    private void applyToAll() {
        List<BlockPos> current = currentRoute();
        if (current.size() < 2) {
            setStatus("§cМаршрут слишком короткий!");
            return;
        }

        List<BlockPos> sharedWaypoints = current.subList(1, current.size());
        for (int i = 0; i < allRoutes.size(); i++) {
            if (i != currentEditIndex) {
                allRoutes.get(i).clear();

                autoPlaceFirstWaypoint(i);

                for (BlockPos wp : sharedWaypoints) {
                    allRoutes.get(i).add(new BlockPos(wp.getX(), wp.getY(), wp.getZ()));
                }
            }
        }
        setStatus("§aМаршрут применён ко всем ПУ");
    }

    private int findNearestWaypointOnProfile(double mouseX) {
        List<BlockPos> route = currentRoute();
        if (route.isEmpty() || profileW <= 0)
            return -1;
        int li = currentEditIndex;
        int totalSegments = route.size();
        double[] segDists = new double[totalSegments];
        double totalDist = 0;
        BlockPos prev = currentLauncherPos();
        for (int i = 0; i < totalSegments; i++) {
            BlockPos wp = route.get(i);
            double dx = wp.getX() - prev.getX();
            double dz = wp.getZ() - prev.getZ();
            segDists[i] = Math.sqrt(dx * dx + dz * dz);
            totalDist += segDists[i];
            prev = wp;
        }
        if (totalDist < 1)
            return -1;
        double bestDist = Double.MAX_VALUE;
        int bestIdx = -1;
        double distAccum = 0;
        for (int i = 0; i < totalSegments; i++) {
            distAccum += segDists[i];
            int sx = profileX + (int) (distAccum / totalDist * (profileW - 1));
            double d = Math.abs(mouseX - sx);
            if (d < bestDist && d < 20 && i > 0 && i < totalSegments - 1) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static final int ALT_MIN = 80;
    private static final int ALT_MAX = 220;
    private static final double ALT_RATE = 34.0; 

    private void updateWaypointAltFromMouse(int wpIdx, double mouseY) {
        if (profileH <= 2)
            return;
        List<BlockPos> route = currentRoute();
        if (wpIdx < 0 || wpIdx >= route.size())
            return;
        if (wpIdx == 0)
            return;
        if (route.size() > 1 && wpIdx == route.size() - 1)
            return;
        int[] bounds = getProfileDisplayBounds();
        int displayMin = bounds[0], displayRange = bounds[1];
        double frac = 1.0 - (mouseY - profileY - 1) / (double) (profileH - 2);
        frac = Math.max(0, Math.min(1, frac));
        int newAlt = displayMin + (int) (frac * displayRange);
        newAlt = Math.max(ALT_MIN, Math.min(ALT_MAX, newAlt));

        BlockPos prev = route.get(wpIdx - 1);
        BlockPos wp = route.get(wpIdx);
        double dx = wp.getX() - prev.getX();
        double dz = wp.getZ() - prev.getZ();
        double segDist = Math.sqrt(dx * dx + dz * dz);
        int maxDelta = Math.max(10, (int) (segDist / 500.0 * ALT_RATE));
        int prevAlt = prev.getY();
        if (newAlt > prevAlt + maxDelta)
            newAlt = prevAlt + maxDelta;
        if (newAlt < prevAlt - maxDelta)
            newAlt = prevAlt - maxDelta;
        newAlt = Math.max(ALT_MIN, Math.min(ALT_MAX, newAlt));
        route.set(wpIdx, new BlockPos(wp.getX(), newAlt, wp.getZ()));
        cascadeAltitude(wpIdx);
    }

    private void cascadeAltitude(int changedIdx) {
        List<BlockPos> route = currentRoute();
        int lastEditable = route.size() > 1 ? route.size() - 2 : route.size() - 1;

        for (int i = changedIdx + 1; i <= lastEditable; i++) {
            BlockPos prev = route.get(i - 1);
            BlockPos curr = route.get(i);
            double dx = curr.getX() - prev.getX();
            double dz = curr.getZ() - prev.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            int maxD = Math.max(10, (int) (dist / 500.0 * ALT_RATE));
            int alt = curr.getY();
            int pAlt = prev.getY();
            if (alt > pAlt + maxD)
                alt = pAlt + maxD;
            if (alt < pAlt - maxD)
                alt = pAlt - maxD;
            alt = Math.max(ALT_MIN, Math.min(ALT_MAX, alt));
            if (alt != curr.getY())
                route.set(i, new BlockPos(curr.getX(), alt, curr.getZ()));
        }

        for (int i = changedIdx - 1; i > 0; i--) {
            BlockPos next = route.get(i + 1);
            BlockPos curr = route.get(i);
            double dx = curr.getX() - next.getX();
            double dz = curr.getZ() - next.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            int maxD = Math.max(10, (int) (dist / 500.0 * ALT_RATE));
            int alt = curr.getY();
            int nAlt = next.getY();
            if (alt > nAlt + maxD)
                alt = nAlt + maxD;
            if (alt < nAlt - maxD)
                alt = nAlt - maxD;
            alt = Math.max(ALT_MIN, Math.min(ALT_MAX, alt));
            if (alt != curr.getY())
                route.set(i, new BlockPos(curr.getX(), alt, curr.getZ()));
        }
    }

    private int[] getProfileDisplayBounds() {
        List<BlockPos> route = currentRoute();
        int li = currentEditIndex;
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        for (int i = 0; i < route.size(); i++) {
            int segKey = li * 100 + i;
            int[] profile = SurfaceHeightCache.getSegmentProfile(segKey);
            if (profile != null) {
                for (int h : profile) {
                    int abs = Math.abs(h);
                    if (abs < minH)
                        minH = abs;
                    if (abs > maxH)
                        maxH = abs;
                }
            }
        }
        int launcherY = currentLauncherPos().getY();
        if (launcherY < minH)
            minH = launcherY;
        if (launcherY > maxH)
            maxH = launcherY;
        for (int i = 0; i < route.size(); i++) {
            BlockPos wp = route.get(i);
            if (i < route.size() - 1) {
                if (wp.getY() > maxH)
                    maxH = wp.getY();
                if (wp.getY() < minH)
                    minH = wp.getY();
            }
        }
        if (0 < minH)
            minH = 0;
        if (maxH <= minH) {
            maxH = minH + 20;
        }
        int hRange = maxH - minH;
        int margin = Math.max(10, hRange / 5);
        int displayMin = minH - margin;
        int displayRange = (maxH + margin) - displayMin;
        if (displayRange < 20)
            displayRange = 20;
        return new int[] { displayMin, displayRange };
    }

    private void onSaveClicked() {

        for (int i = 0; i < allRoutes.size(); i++) {
            List<BlockPos> route = allRoutes.get(i);
            if (route.size() < 2) {
                setStatus("§cДобавьте цель маршрута (минимум 2 точки)!");
                return;
            }
            BlockPos lPos = launcherPositions.get(i);
            BlockPos target = route.get(route.size() - 1);
            double distToTarget = Math.sqrt(
                    Math.pow(target.getX() - lPos.getX(), 2) + Math.pow(target.getZ() - lPos.getZ(), 2));
            if (distToTarget < MIN_TARGET_DISTANCE) {
                setStatus("§cЦель должна быть минимум " + MIN_TARGET_DISTANCE + " бл. от ПУ!");
                return;
            }
        }

        for (int i = 0; i < launcherPositions.size(); i++) {
            NetworkHandler.sendToServer(new SaveShahedRoutePacket(
                    launcherPositions.get(i), new ArrayList<>(allRoutes.get(i))));
        }

        int saved = 0;
        for (List<BlockPos> r : allRoutes)
            if (!r.isEmpty())
                saved++;
        setStatus("§aСохранено: " + saved + "/" + launcherPositions.size());

        if (onSaveCallback != null) {
            onSaveCallback.accept(allRoutes);
        }

        Minecraft.getInstance().setScreen(parentScreen);
    }

    private void setStatus(String msg) {
        this.statusMessage = msg;
        this.statusTicks = 60;
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0)
            statusTicks--;

        if (terrainRenderer != null && !terrainRenderer.hasCacheData()) {
            mapRequestTicks++;
            if (mapRequestTicks >= MAP_REQUEST_INTERVAL) {
                mapRequestTicks = 0;
                NetworkHandler.sendToServer(new RequestRouteMapPacket(launcherPositions.get(0)));
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (terrainRenderer != null) {
            terrainRenderer.close();
            terrainRenderer = null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int mapX = guiLeft + MAP_MARGIN;
        int mapY = guiTop + MAP_MARGIN + 20;
        int rightPanel = mapX + MAP_SIZE + 10;

        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_COLOR);

        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 2, BORDER_COLOR);
        g.fill(guiLeft, guiTop + GUI_HEIGHT - 2, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BORDER_COLOR);
        g.fill(guiLeft, guiTop, guiLeft + 2, guiTop + GUI_HEIGHT, BORDER_COLOR);
        g.fill(guiLeft + GUI_WIDTH - 2, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BORDER_COLOR);

        String title = launcherPositions.size() > 1 ? "МАРШРУТЫ ШАХЕДОВ" : "МАРШРУТ ШАХЕДА";
        int titleW = this.font.width(title);
        g.drawString(this.font, title, guiLeft + (GUI_WIDTH - titleW) / 2, guiTop + 6, BORDER_COLOR, false);

        renderMap(g, mapX, mapY);

        renderHeightProfile(g, mapX, mapY + MAP_SIZE + 12, MAP_SIZE, PROFILE_HEIGHT);

        renderInfoPanel(g, rightPanel, guiTop, mouseX, mouseY);

        this.saveButton.render(g, mouseX, mouseY, partialTick);
        this.clearButton.render(g, mouseX, mouseY, partialTick);
        this.backButton.render(g, mouseX, mouseY, partialTick);
        if (launcherPositions.size() > 1) {
            this.prevLauncherBtn.render(g, mouseX, mouseY, partialTick);
            this.nextLauncherBtn.render(g, mouseX, mouseY, partialTick);
            this.applyAllBtn.render(g, mouseX, mouseY, partialTick);
        }

        this.coordXField.render(g, mouseX, mouseY, partialTick);
        this.coordZField.render(g, mouseX, mouseY, partialTick);
        this.coordAddBtn.render(g, mouseX, mouseY, partialTick);

        if (statusTicks > 0 && !statusMessage.isEmpty()) {
            g.drawCenteredString(this.font, statusMessage, guiLeft + GUI_WIDTH / 2, guiTop + GUI_HEIGHT - 12, 0xFFFFFF);
        }

        this.routeNameField.render(g, mouseX, mouseY, partialTick);
        this.saveRouteBtn.render(g, mouseX, mouseY, partialTick);
        this.deleteRouteBtn.render(g, mouseX, mouseY, partialTick);
        for (Button b : routeButtons) {
            if (b.visible)
                b.render(g, mouseX, mouseY, partialTick);
        }

        g.drawString(this.font, "Добавить точку ВРУЧНУЮ", rightPanel, guiTop + GUI_HEIGHT - 115, 0xAAAAAA, false);
        g.drawString(this.font, "Сохраненные маршруты:", rightPanel, guiTop + GUI_HEIGHT - 332, 0xAAAAAA, false);

        if (isOnMap(mouseX, mouseY)) {
            int wx = mapToWorldX(mouseX);
            int wz = mapToWorldZ(mouseY);
            String coords = String.format("[%d, %d]", wx, wz);
            g.drawString(this.font, coords, mouseX + 12, mouseY - 4, 0xCCCCCC, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private boolean isInsideMap(int sx, int sy) {
        return sx >= clipMinX && sx < clipMaxX && sy >= clipMinY && sy < clipMaxY;
    }

    private void renderMap(GuiGraphics g, int mapX, int mapY) {
        g.fill(mapX, mapY, mapX + MAP_SIZE, mapY + MAP_SIZE, 0xFF0A0A0A);
        renderTerrainTexture(g, mapX, mapY);

        renderLaunchZone(g, mapX, mapY);

        int cx = mapX + MAP_SIZE / 2;
        int cy = mapY + MAP_SIZE / 2;
        g.drawString(this.font, "N", cx - 2, mapY + 2, 0x88FFFFFF, false);
        g.drawString(this.font, "S", cx - 2, mapY + MAP_SIZE - 10, 0x88FFFFFF, false);
        g.drawString(this.font, "W", mapX + 2, cy - 4, 0x88FFFFFF, false);
        g.drawString(this.font, "E", mapX + MAP_SIZE - 8, cy - 4, 0x88FFFFFF, false);

        for (int li = 0; li < launcherPositions.size(); li++) {
            BlockPos lPos = launcherPositions.get(li);
            int lx = worldToMapX(lPos.getX());
            int lz = worldToMapZ(lPos.getZ());
            int mColor = MULTI_COLORS[li % MULTI_COLORS.length];
            int rColor = ROUTE_COLORS[li % ROUTE_COLORS.length];
            boolean isCurrent = (li == currentEditIndex);

            List<BlockPos> route = allRoutes.get(li);
            for (int i = 0; i < route.size(); i++) {
                BlockPos wp = route.get(i);
                int wx = worldToMapX(wp.getX());
                int wz = worldToMapZ(wp.getZ());
                int psx, psz;
                if (i == 0) {
                    psx = lx;
                    psz = lz;
                } else {
                    BlockPos prev = route.get(i - 1);
                    psx = worldToMapX(prev.getX());
                    psz = worldToMapZ(prev.getZ());
                }

                drawLine(g, psx, psz, wx, wz, isCurrent ? rColor : (rColor & 0x70FFFFFF));

                if (isInsideMap(wx, wz)) {
                    boolean isLast = (i == route.size() - 1);
                    int wpColor = isCurrent ? (isLast ? TARGET_COLOR : mColor) : (mColor & 0x60FFFFFF);
                    drawMarker(g, wx, wz, wpColor, isLast ? 5 : (isCurrent ? 4 : 3));

                    if (isCurrent) {
                        String label = isLast ? "★" : String.valueOf(i + 1);
                        g.drawString(this.font, label, wx + 5, wz - 4, wpColor, false);
                        String altLabel = "Y:" + wp.getY();
                        g.drawString(this.font, altLabel, wx + 5, wz + 6, 0xAABBBBBB, false);

                        int segKey = li * 100 + i;
                        Integer segMax = segmentMaxHeights.get(segKey);
                        int nextY = wz + 15;
                        if (segMax != null) {
                            g.drawString(this.font, "▲" + segMax, wx + 5, nextY, 0xAAFF8844, false);
                            nextY += 9;
                        }

                        BlockPos prevPt = (i == 0) ? lPos : route.get(i - 1);
                        int segDist = (int) Math.sqrt(
                                Math.pow(wp.getX() - prevPt.getX(), 2) + Math.pow(wp.getZ() - prevPt.getZ(), 2));
                        g.drawString(this.font, "→" + segDist + " бл.", wx + 5, nextY, 0xAAFFFF44, false);
                    }
                }
            }

            if (isInsideMap(lx, lz)) {
                drawMarker(g, lx, lz, mColor, isCurrent ? 5 : 4);
                String label = launcherPositions.size() > 1 ? "ПУ" + (li + 1) : "ПУ";
                g.drawString(this.font, label, lx + 6, lz - 4, mColor, false);
            }
        }

        if (Minecraft.getInstance().player != null) {
            Vec3 ppos = Minecraft.getInstance().player.position();
            int px = worldToMapX(ppos.x);
            int pz = worldToMapZ(ppos.z);
            if (isInsideMap(px, pz)) {
                drawMarker(g, px, pz, PLAYER_COLOR, 3);
            }
        }

        g.fill(mapX - 1, mapY - 1, mapX + MAP_SIZE + 1, mapY, 0xFF333333);
        g.fill(mapX - 1, mapY + MAP_SIZE, mapX + MAP_SIZE + 1, mapY + MAP_SIZE + 1, 0xFF333333);
        g.fill(mapX - 1, mapY, mapX, mapY + MAP_SIZE, 0xFF333333);
        g.fill(mapX + MAP_SIZE, mapY, mapX + MAP_SIZE + 1, mapY + MAP_SIZE, 0xFF333333);

        g.drawString(this.font, String.format("1px = %.0f бл.", scale), mapX + 2, mapY + MAP_SIZE + 3, 0x888888, false);
    }

    private void renderTerrainTexture(GuiGraphics g, int mapX, int mapY) {
        if (terrainRenderer == null || !terrainRenderer.isReady())
            return;

        if (terrainRenderer.hasCacheData()) {
            int texSize = terrainRenderer.getTextureSize();
            double bpp = ServerRouteMapScanner.CACHE_BPP;

            double viewRadius = (MAP_SIZE / 2.0) * scale;

            double panDeltaX = viewCenterX - terrainRenderer.getCenterX();
            double panDeltaZ = viewCenterZ - terrainRenderer.getCenterZ();

            float srcLeft = (float) (texSize / 2.0 + panDeltaX / bpp - viewRadius / bpp);
            float srcTop = (float) (texSize / 2.0 + panDeltaZ / bpp - viewRadius / bpp);
            float srcRight = (float) (texSize / 2.0 + panDeltaX / bpp + viewRadius / bpp);
            float srcBottom = (float) (texSize / 2.0 + panDeltaZ / bpp + viewRadius / bpp);

            int dstLeft = mapX;
            int dstTop = mapY;
            int dstRight = mapX + MAP_SIZE;
            int dstBottom = mapY + MAP_SIZE;

            float fullSrcW = srcRight - srcLeft;
            float fullSrcH = srcBottom - srcTop;
            if (fullSrcW <= 0 || fullSrcH <= 0)
                return;

            if (srcLeft < 0) {
                dstLeft += (int) (-srcLeft / fullSrcW * MAP_SIZE);
                srcLeft = 0;
            }
            if (srcTop < 0) {
                dstTop += (int) (-srcTop / fullSrcH * MAP_SIZE);
                srcTop = 0;
            }
            if (srcRight > texSize) {
                dstRight -= (int) ((srcRight - texSize) / fullSrcW * MAP_SIZE);
                srcRight = texSize;
            }
            if (srcBottom > texSize) {
                dstBottom -= (int) ((srcBottom - texSize) / fullSrcH * MAP_SIZE);
                srcBottom = texSize;
            }

            int dstW = dstRight - dstLeft;
            int dstH = dstBottom - dstTop;
            int srcW = Math.max(1, (int) (srcRight - srcLeft));
            int srcH = Math.max(1, (int) (srcBottom - srcTop));

            if (dstW > 0 && dstH > 0) {
                RenderSystem.setShaderTexture(0, terrainRenderer.getTextureLocation());
                g.blit(terrainRenderer.getTextureLocation(),
                        dstLeft, dstTop, dstW, dstH,
                        srcLeft, srcTop, srcW, srcH,
                        texSize, texSize);
            }
        } else {

            String loadText;
            int loadColor = 0xFF40FF80;
            if (terrainRenderer.isQueued()) {
                loadText = "В очереди: #" + terrainRenderer.getQueuePosition();
                loadColor = 0xFFffcc40;
            } else if (terrainRenderer.isBuilding()) {
                int pct = (int) (terrainRenderer.getBuildProgress() * 100);
                loadText = "Сканирование: " + pct + "%";
            } else {
                loadText = "Загрузка карты...";
            }
            int tw = this.font.width(loadText);
            g.drawString(this.font, loadText, mapX + (MAP_SIZE - tw) / 2, mapY + MAP_SIZE / 2 - 4, loadColor, false);
        }
    }

    private void renderLaunchZone(GuiGraphics g, int mapX, int mapY) {
        BlockPos lPos = currentLauncherPos();
        double maxAngle = Math.toRadians(LauncherZoneValidator.MAX_DEVIATION_ANGLE);
        double yawRad = Math.toRadians(currentLauncherYaw());
        double range = (MAP_SIZE / 2.0) * scale * 1.5;

        int cx = worldToMapX(lPos.getX());
        int cy = worldToMapZ(lPos.getZ());

        double lx = lPos.getX() + (-Math.sin(yawRad - maxAngle)) * range;
        double lz = lPos.getZ() + Math.cos(yawRad - maxAngle) * range;
        double rx = lPos.getX() + (-Math.sin(yawRad + maxAngle)) * range;
        double rz = lPos.getZ() + Math.cos(yawRad + maxAngle) * range;
        drawLine(g, cx, cy, worldToMapX(lx), worldToMapZ(lz), 0x6000FF00);
        drawLine(g, cx, cy, worldToMapX(rx), worldToMapZ(rz), 0x6000FF00);

        int arcSegments = 8;
        for (int i = 0; i < arcSegments; i++) {
            double t1 = -maxAngle + (2.0 * maxAngle * i / arcSegments);
            double t2 = -maxAngle + (2.0 * maxAngle * (i + 1) / arcSegments);
            double ax1 = lPos.getX() + (-Math.sin(yawRad + t1)) * range;
            double az1 = lPos.getZ() + Math.cos(yawRad + t1) * range;
            double ax2 = lPos.getX() + (-Math.sin(yawRad + t2)) * range;
            double az2 = lPos.getZ() + Math.cos(yawRad + t2) * range;
            drawLine(g, worldToMapX(ax1), worldToMapZ(az1), worldToMapX(ax2), worldToMapZ(az2), 0x4000FF00);
        }
    }

    private void renderInfoPanel(GuiGraphics g, int x, int guiTop, int mouseX, int mouseY) {
        int y = guiTop + 25;

        if (launcherPositions.size() > 1) {
            String label = "ПУ #" + (currentEditIndex + 1) + "/" + launcherPositions.size();
            g.drawString(this.font, label, x + 25, y, MULTI_COLORS[currentEditIndex % MULTI_COLORS.length], false);
            y += 18;
        }

        g.drawString(this.font, "МАРШРУТ:", x, y, 0xAAAAAA, false);
        y += 12;

        List<BlockPos> route = currentRoute();
        BlockPos lPos = currentLauncherPos();

        if (route.isEmpty()) {
            g.drawString(this.font, "§7(пусто)", x, y, 0x666666, false);
            g.drawString(this.font, "§7ЛКМ - точка", x, y + 12, 0x555555, false);
            g.drawString(this.font, "§7ПКМ - удалить", x, y + 22, 0x555555, false);
            g.drawString(this.font, "§7Скролл - зум", x, y + 32, 0x555555, false);
            g.drawString(this.font, "§7СКМ - панорама", x, y + 42, 0x555555, false);
        } else {
            int maxVisible = 6;
            int startIdx = Math.max(0, route.size() - maxVisible);
            for (int i = startIdx; i < route.size(); i++) {
                BlockPos wp = route.get(i);
                boolean isLast = (i == route.size() - 1);
                String prefix = isLast ? "\u2605" : (i + 1) + ".";
                int color = isLast ? 0xFFFF3333 : 0xFF00CC00;
                String text = String.format("%s [%d, %d] Y:%d", prefix, wp.getX(), wp.getZ(), wp.getY());
                g.drawString(this.font, text, x, y, color, false);
                y += 11;
            }

            y += 6;

            double totalDist = calculateTotalDistance();
            g.drawString(this.font, String.format("\u0414\u0438\u0441\u0442: %.0f \u0431\u043b.", totalDist), x, y,
                    0xAAAAAA, false);
            y += 13;

            double cruiseSpeed = ShahedConfig.getShahedCruiseSpeed();
            if (cruiseSpeed > 0) {
                double ticks = totalDist / cruiseSpeed;
                double seconds = ticks / 20.0;
                g.drawString(this.font,
                        String.format("\u0412\u0440\u0435\u043c\u044f: ~%.0f \u0441\u0435\u043a", seconds), x, y,
                        0xAAAAAA, false);
            }
            y += 13;

            g.drawString(this.font, String.format("\u041c\u0430\u043a\u0441: %d \u0431\u043b.", MAX_FLIGHT_DISTANCE), x,
                    y, 0x666666, false);
            y += 14;

            BlockPos first = route.get(0);
            boolean inZone = LauncherZoneValidator.isTargetInValidZone(lPos, currentLauncherYaw(), first);
            if (!inZone) {
                g.drawString(this.font, "§cВне зоны ПУ!", x, y, 0xFF5555, false);
            }
        }

        y = guiTop + GUI_HEIGHT - 100;
        boolean hasDrone = currentEditIndex < hasDroneList.size() && hasDroneList.get(currentEditIndex);
        String droneStatus = hasDrone ? "§aДРОН ГОТОВ" : "§cНЕТ ДРОНА";
        g.drawString(this.font, droneStatus, x, y, hasDrone ? 0x55FF55 : 0xFF5555, false);
    }

    private double calculateTotalDistance() {
        List<BlockPos> route = currentRoute();
        if (route.isEmpty())
            return 0;
        BlockPos lPos = currentLauncherPos();
        double total = 0;
        double prevX = lPos.getX();
        double prevZ = lPos.getZ();
        for (BlockPos wp : route) {
            double dx = wp.getX() - prevX;
            double dz = wp.getZ() - prevZ;
            total += Math.sqrt(dx * dx + dz * dz);
            prevX = wp.getX();
            prevZ = wp.getZ();
        }
        return total;
    }

    private void drawMarker(GuiGraphics g, int x, int y, int color, int size) {
        g.fill(x - size, y - size, x + size, y + size, color);

        if (size > 2) {
            g.fill(x - size + 1, y - size + 1, x + size - 1, y + size - 1, 0xFF000000);
            g.fill(x - size + 2, y - size + 2, x + size - 2, y + size - 2, color);
        }
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int steps = 0;
        int maxSteps = dx + dy + 1;
        while (steps < maxSteps) {

            if (x1 >= clipMinX && x1 < clipMaxX && y1 >= clipMinY && y1 < clipMaxY) {
                g.fill(x1, y1, x1 + 1, y1 + 1, color);
            }
            if (x1 == x2 && y1 == y2)
                break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
            steps++;
        }
    }

    public void onHeightReceived(int x, int z, int y) {

    }

    public void onSegmentHeightReceived(int segmentIndex, int maxY) {
        segmentMaxHeights.put(segmentIndex, maxY);
    }

    private void requestSegmentHeightForWaypoint(int wpIdx) {
        List<BlockPos> route = currentRoute();
        if (wpIdx < 0 || wpIdx >= route.size())
            return;
        BlockPos wp = route.get(wpIdx);
        BlockPos prev;
        if (wpIdx == 0) {
            prev = currentLauncherPos();
        } else {
            prev = route.get(wpIdx - 1);
        }
        int segKey = currentEditIndex * 100 + wpIdx;
        SurfaceHeightCache.requestSegmentHeight(prev.getX(), prev.getZ(), wp.getX(), wp.getZ(), segKey);
    }

    private void renderHeightProfile(GuiGraphics g, int px, int py, int pw, int ph) {
        this.profileX = px;
        this.profileY = py;
        this.profileW = pw;
        this.profileH = ph;

        List<BlockPos> route = currentRoute();
        if (route.isEmpty()) {
            g.fill(px, py, px + pw, py + ph, 0xCC0A0A0A);
            g.drawString(this.font,
                    "\u0414\u043e\u0431\u0430\u0432\u044c\u0442\u0435 \u0442\u043e\u0447\u043a\u0438 \u043c\u0430\u0440\u0448\u0440\u0443\u0442\u0430",
                    px + pw / 2 - 55, py + ph / 2 - 4, 0x555555, false);
            return;
        }

        g.fill(px, py, px + pw, py + ph, 0xCC0A0A0A);

        int li = currentEditIndex;
        int totalSegments = route.size();
        java.util.List<int[]> profiles = new java.util.ArrayList<>();
        double[] segDists = new double[totalSegments];
        double totalDist = 0;
        BlockPos prev = currentLauncherPos();
        for (int i = 0; i < totalSegments; i++) {
            BlockPos wp = route.get(i);
            double dx = wp.getX() - prev.getX();
            double dz = wp.getZ() - prev.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            segDists[i] = d;
            totalDist += d;
            int segKey = li * 100 + i;
            profiles.add(SurfaceHeightCache.getSegmentProfile(segKey));
            prev = wp;
        }
        if (totalDist < 1)
            return;

        int[] bounds = getProfileDisplayBounds();
        int displayMin = bounds[0], displayRange = bounds[1];

        int lastScaleTextY = -100;
        for (int step = 0; step <= 4; step++) {
            int h = displayMin + displayRange * step / 4;
            int sy = py + ph - 1 - (int) ((double) (h - displayMin) / displayRange * (ph - 2));
            if (sy > py + 10 && sy < py + ph - 1) {
                for (int xi = px; xi < px + pw; xi += 4) {
                    g.fill(xi, sy, xi + 1, sy + 1, 0x18FFFFFF);
                }
                if (Math.abs(sy - lastScaleTextY) > 10) {
                    String hStr = String.valueOf(h);
                    int hStrW = this.font.width(hStr);
                    g.drawString(this.font, hStr, px + pw - hStrW - 2, sy - 4, 0x44FFFFFF, false);
                    lastScaleTextY = sy;
                }
            }
        }

        double distAccum = 0;
        for (int seg = 0; seg < totalSegments; seg++) {
            int[] profile = profiles.get(seg);
            if (profile == null) {
                distAccum += segDists[seg];
                continue;
            }
            int samplesN = profile.length;
            for (int s = 0; s < samplesN - 1; s++) {
                double t0 = distAccum + segDists[seg] * s / (samplesN - 1);
                double t1 = distAccum + segDists[seg] * (s + 1) / (samplesN - 1);
                int sx0 = px + (int) (t0 / totalDist * (pw - 1));
                int sx1 = px + (int) (t1 / totalDist * (pw - 1));
                int raw0 = profile[s];
                int raw1 = profile[s + 1];
                boolean ocean0 = raw0 < 0;
                boolean ocean1 = raw1 < 0;
                int h0 = Math.abs(raw0);
                int h1 = Math.abs(raw1);
                for (int xi = sx0; xi <= sx1 && xi < px + pw; xi++) {
                    double frac = (sx1 == sx0) ? 0 : (double) (xi - sx0) / (sx1 - sx0);
                    int terrainH = (int) (h0 + (h1 - h0) * frac);
                    boolean isOcean = frac < 0.5 ? ocean0 : ocean1;
                    int tsy = py + ph - 1 - (int) ((double) (terrainH - displayMin) / displayRange * (ph - 2));
                    tsy = Math.max(py + 1, Math.min(tsy, py + ph - 2));
                    double distAtXi = t0 + (t1 - t0) * frac;
                    int droneH = getDroneAltitudeAtDist(distAtXi, totalDist, segDists, route);
                    boolean danger = terrainH >= droneH - 5;
                    if (danger) {
                        g.fill(xi, tsy, xi + 1, py + ph - 1, 0x80AA2222);
                        g.fill(xi, tsy, xi + 1, tsy + 1, 0xFFFF4444);
                    } else if (isOcean) {
                        g.fill(xi, tsy, xi + 1, py + ph - 1, 0x40226688);
                        g.fill(xi, tsy, xi + 1, tsy + 1, 0xFF44AADD);
                    } else {
                        g.fill(xi, tsy, xi + 1, py + ph - 1, 0x4044AA44);
                        g.fill(xi, tsy, xi + 1, tsy + 1, 0xFF55CC55);
                    }
                }
            }
            distAccum += segDists[seg];
        }

        int prevDsy = -1;
        for (int xi = px; xi < px + pw; xi++) {
            double dist = (double) (xi - px) / (pw - 1) * totalDist;
            int droneH = getDroneAltitudeAtDist(dist, totalDist, segDists, route);
            int dsy = py + ph - 1 - (int) ((double) (droneH - displayMin) / displayRange * (ph - 2));
            dsy = Math.max(py + 1, Math.min(dsy, py + ph - 2));
            g.fill(xi, dsy, xi + 1, dsy + 1, 0xFFFF6644);
            if (prevDsy >= 0 && Math.abs(dsy - prevDsy) > 1) {
                int minY = Math.min(dsy, prevDsy);
                int maxY = Math.max(dsy, prevDsy);
                g.fill(xi, minY, xi + 1, maxY + 1, 0xAAFF6644);
            }
            prevDsy = dsy;
        }

        distAccum = 0;
        int lastLabelEndX = px - 100;
        for (int i = 0; i < totalSegments; i++) {
            distAccum += segDists[i];
            int sx = px + (int) (distAccum / totalDist * (pw - 1));

            sx = Math.max(px + 3, Math.min(sx, px + pw - 4));
            for (int yi = py + 12; yi < py + ph - 12; yi += 3) {
                g.fill(sx, yi, sx + 1, yi + 1, 0x33FFFF00);
            }
            BlockPos wp = route.get(i);
            int wpAlt = wp.getY();
            boolean isDrag = (i == draggingWaypointIndex);
            boolean isFirst = (i == 0);
            boolean isLast = (i == totalSegments - 1 && totalSegments > 1);
            int wsy;
            if (isLast) {
                wsy = py + ph - 12;
            } else {
                wsy = py + ph - 1 - (int) ((double) (wpAlt - displayMin) / displayRange * (ph - 2));
                wsy = Math.max(py + 12, Math.min(wsy, py + ph - 12));
            }
            int dotColor = isFirst ? 0xFF88FF88 : (isLast ? 0xFFFF4444 : (isDrag ? 0xFFFFFF00 : 0xFFFF6644));
            g.fill(sx - 2, wsy - 2, sx + 3, wsy + 3, 0xFF000000);
            g.fill(sx - 1, wsy - 1, sx + 2, wsy + 2, dotColor);

            String wpLabel = isFirst ? "0" : (isLast ? "\u2605" : String.valueOf(i + 1));
            int labelColor = isFirst ? 0xFF88FF88 : (isLast ? 0xFFFF4444 : 0xFFFFFF00);
            int wpLabelW = this.font.width(wpLabel);
            int wpLabelX = Math.max(px + 1, Math.min(sx - wpLabelW / 2, px + pw - wpLabelW - 1));
            g.drawString(this.font, wpLabel, wpLabelX, py + ph - 10, labelColor, false);

            String yText = isLast ? "\u0426\u0415\u041b\u042c" : (wpAlt + "m");
            int yTextW = this.font.width(yText);
            int labelX = sx - yTextW / 2;
            if (labelX < px + 1)
                labelX = px + 1;
            if (labelX + yTextW > px + pw - 1)
                labelX = px + pw - 1 - yTextW;

            if (isLast || isDrag || labelX >= lastLabelEndX + 2) {
                g.fill(labelX - 1, py + 1, labelX + yTextW + 1, py + 11, 0xCC000000);
                g.drawString(this.font, yText, labelX, py + 2, dotColor, false);
                lastLabelEndX = labelX + yTextW;
            }
        }

        g.fill(px, py, px + pw, py + 1, 0xFF334433);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0xFF334433);
        g.fill(px, py, px + 1, py + ph, 0xFF334433);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0xFF334433);
        String profileTitle = "\u041f\u0420\u041e\u0424\u0418\u041b\u042c \u0420\u0415\u041b\u042c\u0415\u0424\u0410";
        g.drawString(this.font, profileTitle, px + pw / 2 - this.font.width(profileTitle) / 2, py - 10, 0xAA55CC55,
                false);
    }

    private int getDroneAltitudeAtDist(double dist, double totalDist, double[] segDists, List<BlockPos> route) {
        if (totalDist < 1 || route.isEmpty())
            return DEFAULT_WAYPOINT_ALT;
        int prevAlt = currentLauncherPos().getY();
        double accum = 0;
        for (int i = 0; i < segDists.length; i++) {
            double segEnd = accum + segDists[i];
            boolean isLastSeg = (i == segDists.length - 1 && segDists.length > 1);
            int curAlt = route.get(i).getY();
            if (dist <= segEnd) {
                double segFrac = segDists[i] > 0 ? (dist - accum) / segDists[i] : 0;
                if (isLastSeg) {

                    if (segFrac < 0.7) {
                        return prevAlt;
                    } else {
                        double diveFrac = (segFrac - 0.7) / 0.3;
                        return (int) (prevAlt * (1.0 - diveFrac));
                    }
                }
                return (int) (prevAlt + (curAlt - prevAlt) * segFrac);
            }
            accum = segEnd;
            prevAlt = isLastSeg ? 0 : curAlt;
        }
        return 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
