package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.OrlanCameraHandler;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.network.LaunchOrlanFromTabletPacket;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.OrlanCameraViewPacket;
import com.synarsis.airtacticalarsenal.network.OrlanReturnPacket;
import com.synarsis.airtacticalarsenal.network.RemoveLauncherFromTabletPacket;
import com.synarsis.airtacticalarsenal.network.UpdateOrlanRoutePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class OrlanTabletScreen extends Screen {

    private static final int GUI_WIDTH = 420;
    private static final int GUI_HEIGHT = 360;

    private static final int BORDER_COLOR = 0xFF1565C0;
    private static final int BG_COLOR = 0xE0101010;
    private static final int ACCENT_COLOR = 0xFF42A5F5;

    private final List<BlockPos> launcherPositions;
    private final List<Boolean> hasDroneList;
    private final List<Float> launcherYawList;
    private final Map<Integer, int[]> savedCoords = new HashMap<>();
    private final List<int[]> activeOrlans;
    private final List<List<BlockPos>> savedRoutes;

    private int selectedLauncherIndex = -1;
    private final Set<Integer> checkedLaunchers = new HashSet<>();
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_LAUNCHERS = 5;

    private int droneScrollOffset = 0;
    private static final int MAX_VISIBLE_DRONES = 3;

    private Button launchButton;
    private Button removeButton;
    private Button routeButton;
    private Button checkAllButton;
    private final List<Button> launcherButtons = new ArrayList<>();
    private final List<Button> cameraButtons = new ArrayList<>();
    private final List<Button> routeUpdateButtons = new ArrayList<>();
    private final List<Button> returnButtons = new ArrayList<>();

    private int refreshTicks = 0;
    private final Map<Integer, Integer> localTimerCache = new HashMap<>();

    private String statusMessage = "";

    public OrlanTabletScreen(List<BlockPos> launcherPositions,
                              List<Boolean> hasDroneList,
                              List<Float> launcherYawList,
                              Map<Integer, int[]> savedCoords,
                              List<int[]> activeOrlans,
                              List<List<BlockPos>> savedRoutes) {
        super(Component.literal(""));
        this.launcherPositions = new ArrayList<>(launcherPositions);
        this.hasDroneList = new ArrayList<>(hasDroneList);
        this.launcherYawList = launcherYawList != null ? new ArrayList<>(launcherYawList) : new ArrayList<>();
        if (savedCoords != null) this.savedCoords.putAll(savedCoords);
        this.activeOrlans = activeOrlans != null ? new ArrayList<>(activeOrlans) : new ArrayList<>();
        this.savedRoutes = savedRoutes != null ? new ArrayList<>(savedRoutes) : new ArrayList<>();
    }

    public void onRoutesSaved(List<List<BlockPos>> updatedRoutes) {
        for (int i = 0; i < updatedRoutes.size() && i < savedRoutes.size(); i++) {
            savedRoutes.set(i, new ArrayList<>(updatedRoutes.get(i)));
        }
        rebuildLauncherButtons();
        updateButtonStates();
    }

    @Override
    protected void init() {
        super.init();

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int rightX = guiLeft + 195;
        int btnW = 100;

        rebuildLauncherButtons();
        rebuildCameraButtons();

        this.checkAllButton = Button.builder(
                Component.literal(checkedLaunchers.size() == launcherPositions.size() ? "СНЯТЬ ВСЕ" : "ВЫБРАТЬ ВСЕ"),
                button -> toggleCheckAll())
            .bounds(guiLeft + 15, guiTop + 38 + MAX_VISIBLE_LAUNCHERS * 22 + 4, 160, 18)
            .build();
        this.addWidget(this.checkAllButton);

        this.routeButton = Button.builder(
                Component.literal("МАРШРУТ"),
                button -> onRouteClicked())
            .bounds(rightX, guiTop + 120, btnW, 20)
            .build();
        this.routeButton.active = false;
        this.addWidget(this.routeButton);

        this.removeButton = Button.builder(
                Component.literal("ОТВЯЗАТЬ"),
                button -> onRemoveClicked())
            .bounds(rightX + btnW + 5, guiTop + 120, btnW, 20)
            .build();
        this.removeButton.active = false;
        this.addWidget(this.removeButton);

        this.launchButton = Button.builder(
                Component.literal("ЗАПУСК"),
                button -> onLaunchClicked())
            .bounds(rightX, guiTop + 145, btnW, 20)
            .build();
        this.launchButton.active = false;
        this.addWidget(this.launchButton);

        updateButtonStates();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int dividerX = guiLeft + 185;

        if (mouseX < dividerX) {
            if (launcherPositions.size() > MAX_VISIBLE_LAUNCHERS) {
                scrollOffset = Math.max(0, Math.min(scrollOffset - (int) delta, launcherPositions.size() - MAX_VISIBLE_LAUNCHERS));
                rebuildLauncherButtons();
            }
        } else {
            if (activeOrlans.size() > MAX_VISIBLE_DRONES) {
                droneScrollOffset = Math.max(0, Math.min(droneScrollOffset - (int) delta, activeOrlans.size() - MAX_VISIBLE_DRONES));
                rebuildCameraButtons();
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void rebuildLauncherButtons() {
        for (Button btn : launcherButtons) {
            this.removeWidget(btn);
        }
        launcherButtons.clear();

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        int visibleCount = Math.min(launcherPositions.size() - scrollOffset, MAX_VISIBLE_LAUNCHERS);

        for (int i = 0; i < visibleCount; i++) {
            final int actualIndex = i + scrollOffset;
            boolean hasDrone = actualIndex < hasDroneList.size() && hasDroneList.get(actualIndex);
            boolean hasRoute = actualIndex < savedRoutes.size() && !savedRoutes.get(actualIndex).isEmpty();
            boolean checked = checkedLaunchers.contains(actualIndex);

            String check = checked ? "§a[✓]" : "§7[ ]";
            String droneIcon = hasDrone ? "§a●" : "§c○";
            String routeIcon = hasRoute ? "§a→" : "§8-";
            String text = check + " " + droneIcon + " " + routeIcon + " ПУ#" + (actualIndex + 1);

            if (actualIndex == selectedLauncherIndex) {
                text = "▶" + text;
            }

            Button btn = Button.builder(
                    Component.literal(text),
                    button -> onLauncherButtonClicked(actualIndex))
                .bounds(guiLeft + 10, guiTop + 38 + i * 22, 170, 20)
                .build();

            launcherButtons.add(btn);
            this.addWidget(btn);
        }
    }

    private void onLauncherButtonClicked(int index) {
        if (Screen.hasShiftDown()) {
            toggleCheck(index);
        } else {
            selectLauncher(index);
        }
    }

    private void toggleCheck(int index) {
        if (checkedLaunchers.contains(index)) {
            checkedLaunchers.remove(index);
        } else {
            checkedLaunchers.add(index);
        }
        rebuildLauncherButtons();
        updateButtonStates();
        updateCheckAllButton();
    }

    private void toggleCheckAll() {
        if (checkedLaunchers.size() == launcherPositions.size()) {
            checkedLaunchers.clear();
        } else {
            for (int i = 0; i < launcherPositions.size(); i++) {
                checkedLaunchers.add(i);
            }
        }
        rebuildLauncherButtons();
        updateButtonStates();
        updateCheckAllButton();
    }

    private void updateCheckAllButton() {
        if (checkAllButton != null) {
            checkAllButton.setMessage(Component.literal(
                checkedLaunchers.size() == launcherPositions.size() ? "СНЯТЬ ВСЕ" : "ВЫБРАТЬ ВСЕ"));
        }
    }

    private void rebuildCameraButtons() {
        for (Button btn : cameraButtons) { this.removeWidget(btn); }
        cameraButtons.clear();
        for (Button btn : routeUpdateButtons) { this.removeWidget(btn); }
        routeUpdateButtons.clear();
        for (Button btn : returnButtons) { this.removeWidget(btn); }
        returnButtons.clear();

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int cameraY = guiTop + 210;
        int maxBottom = guiTop + GUI_HEIGHT - 18;

        int visibleDrones = Math.min(activeOrlans.size() - droneScrollOffset, MAX_VISIBLE_DRONES);
        for (int i = 0; i < visibleDrones; i++) {
            int btnY = cameraY + i * 26;
            if (btnY + 20 > maxBottom) break;

            final int actualIdx = i + droneScrollOffset;
            final int[] orlanData = activeOrlans.get(actualIdx);
            final int entityId = orlanData[0];
            final int droneNum = actualIdx + 1;

            int remainTicks = getLiveTimer(entityId, orlanData);
            int totalSec = remainTicks / 20;
            String timerStr = String.format("%02d:%02d", totalSec / 60, totalSec % 60);
            String phaseStr = getLivePhase(entityId);

            String text = "§bОрлан #" + droneNum + " §7[" + orlanData[1] + "," + orlanData[2] + "] " + phaseStr + " §e" + timerStr;

            Button camBtn = Button.builder(
                    Component.literal(text),
                    button -> connectCamera(entityId))
                .bounds(guiLeft + 10, btnY, 315, 20)
                .build();
            cameraButtons.add(camBtn);
            this.addWidget(camBtn);

            Button routeBtn = Button.builder(
                    Component.literal("§6→"),
                    button -> updateDroneRoute(entityId, orlanData))
                .bounds(guiLeft + 330, btnY, 30, 20)
                .build();
            routeUpdateButtons.add(routeBtn);
            this.addWidget(routeBtn);

            Button retBtn = Button.builder(
                    Component.literal("§c↩"),
                    button -> returnDrone(entityId))
                .bounds(guiLeft + 365, btnY, 30, 20)
                .build();
            returnButtons.add(retBtn);
            this.addWidget(retBtn);
        }
    }

    private int getLiveTimer(int entityId, int[] fallbackData) {
        if (localTimerCache.containsKey(entityId)) {
            return Math.max(0, localTimerCache.get(entityId));
        }
        int timer = getLiveTimerFromSource(entityId, fallbackData);
        localTimerCache.put(entityId, timer);
        return timer;
    }

    private String getLivePhase(int entityId) {
        int[] cached = com.synarsis.airtacticalarsenal.network.ClientPacketHandler.getCachedOrlanData(entityId);
        if (cached != null) {
            OrlanEntity.FlightPhase[] phases = OrlanEntity.FlightPhase.values();
            if (cached[0] >= 0 && cached[0] < phases.length) {
                return switch (phases[cached[0]]) {
                    case LAUNCHING, CLIMBING -> "§d▲";
                    case CRUISING -> "§6→";
                    case PATROLLING -> "§a◎";
                    case RETURNING -> "§c↩";
                    case LANDING -> "§e▼";
                    case LANDED -> "§7■";
                };
            }
        }
        return "";
    }

    private void updateDroneRoute(int entityId, int[] orlanData) {

        BlockPos dronePos;
        net.minecraft.world.entity.Entity droneEntity = Minecraft.getInstance().level != null 
                ? Minecraft.getInstance().level.getEntity(entityId) : null;
        if (droneEntity != null) {
            dronePos = droneEntity.blockPosition();
        } else {

            dronePos = new BlockPos(orlanData[1], 64, orlanData[2]);
        }

        float droneYaw = droneEntity != null ? droneEntity.getYRot() : 0f;
        BlockPos centerPos = dronePos; 

        List<BlockPos> positions = new ArrayList<>();
        positions.add(centerPos);
        List<Float> yaws = new ArrayList<>();
        yaws.add(droneYaw);
        List<Boolean> drones = new ArrayList<>();
        drones.add(true);
        List<List<BlockPos>> routes = new ArrayList<>();
        routes.add(new ArrayList<>());

        final int droneEntityId = entityId;
        OrlanRouteScreen routeScreen = new OrlanRouteScreen(this, positions, yaws, drones, routes,
                updatedRoutes -> {
                    if (!updatedRoutes.isEmpty() && !updatedRoutes.get(0).isEmpty()) {
                        List<BlockPos> newRoute = updatedRoutes.get(0);
                        NetworkHandler.sendToServer(new UpdateOrlanRoutePacket(droneEntityId, newRoute));
                        statusMessage = "§aМаршрут обновлён!";
                    }
                }, droneEntityId);
        Minecraft.getInstance().setScreen(routeScreen);
    }

    private void returnDrone(int entityId) {
        NetworkHandler.sendToServer(new OrlanReturnPacket(entityId));
        rebuildCameraButtons();
    }

    private void selectLauncher(int index) {
        if (index < 0 || index >= launcherPositions.size()) return;
        this.selectedLauncherIndex = index;
        rebuildLauncherButtons();
        updateButtonStates();
        this.statusMessage = "";
    }

    private static final double MAX_TABLET_DISTANCE = 30.0;

    private boolean isPlayerInRange(int launcherIdx) {
        if (launcherIdx < 0 || launcherIdx >= launcherPositions.size()) return false;
        if (Minecraft.getInstance().player == null) return false;
        BlockPos lPos = launcherPositions.get(launcherIdx);
        double dist = Minecraft.getInstance().player.position().distanceTo(
                new net.minecraft.world.phys.Vec3(lPos.getX() + 0.5, lPos.getY() + 0.5, lPos.getZ() + 0.5));
        return dist <= MAX_TABLET_DISTANCE;
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedLauncherIndex >= 0 && selectedLauncherIndex < launcherPositions.size();
        boolean hasDrone = hasSelection && selectedLauncherIndex < hasDroneList.size() && hasDroneList.get(selectedLauncherIndex);
        boolean hasRoute = hasSelection && selectedLauncherIndex < savedRoutes.size() && !savedRoutes.get(selectedLauncherIndex).isEmpty();
        boolean inRange = hasSelection && isPlayerInRange(selectedLauncherIndex);

        this.launchButton.active = hasSelection && hasDrone && hasRoute && inRange;
        this.removeButton.active = hasSelection;

        this.routeButton.active = hasSelection || !checkedLaunchers.isEmpty();

        if (hasSelection && !inRange) {
            statusMessage = "§cПУ слишком далеко! Макс. 30 блоков";
        } else if (statusMessage.startsWith("§cПУ слишком")) {
            statusMessage = "";
        }
    }

    private void onRouteClicked() {
        List<Integer> indices = new ArrayList<>();
        if (!checkedLaunchers.isEmpty()) {
            indices.addAll(checkedLaunchers);
            Collections.sort(indices);
        } else if (selectedLauncherIndex >= 0) {
            indices.add(selectedLauncherIndex);
        }
        if (indices.isEmpty()) return;

        List<BlockPos> positions = new ArrayList<>();
        List<Float> yaws = new ArrayList<>();
        List<Boolean> drones = new ArrayList<>();
        List<List<BlockPos>> routes = new ArrayList<>();

        for (int idx : indices) {
            positions.add(launcherPositions.get(idx));
            yaws.add(idx < launcherYawList.size() ? launcherYawList.get(idx) : 0f);
            drones.add(idx < hasDroneList.size() && hasDroneList.get(idx));
            routes.add(idx < savedRoutes.size() ? new ArrayList<>(savedRoutes.get(idx)) : new ArrayList<>());
        }

        final List<Integer> absoluteIndices = new ArrayList<>(indices);
        Minecraft.getInstance().setScreen(new OrlanRouteScreen(this, positions, yaws, drones, routes,
                updatedRoutes -> {
                    for (int i = 0; i < absoluteIndices.size() && i < updatedRoutes.size(); i++) {
                        int absIdx = absoluteIndices.get(i);
                        if (absIdx < savedRoutes.size()) {
                            savedRoutes.set(absIdx, new ArrayList<>(updatedRoutes.get(i)));
                        }
                    }
                    rebuildLauncherButtons();
                    updateButtonStates();
                }));
    }

    private void onLaunchClicked() {
        if (selectedLauncherIndex < 0 || selectedLauncherIndex >= launcherPositions.size()) return;
        BlockPos launcherPos = launcherPositions.get(selectedLauncherIndex);
        NetworkHandler.sendToServer(new LaunchOrlanFromTabletPacket(launcherPos, 0, 0));

        if (selectedLauncherIndex < hasDroneList.size()) {
            hasDroneList.set(selectedLauncherIndex, false);
        }
        statusMessage = "§aЗапуск выполнен!";
        rebuildLauncherButtons();
        updateButtonStates();
    }

    private void onRemoveClicked() {
        if (selectedLauncherIndex < 0 || selectedLauncherIndex >= launcherPositions.size()) return;

        BlockPos posToRemove = launcherPositions.get(selectedLauncherIndex);
        NetworkHandler.sendToServer(new RemoveLauncherFromTabletPacket(posToRemove));

        int removed = selectedLauncherIndex;
        savedCoords.remove(removed);
        launcherPositions.remove(removed);
        if (removed < hasDroneList.size()) hasDroneList.remove(removed);
        if (removed < savedRoutes.size()) savedRoutes.remove(removed);

        Set<Integer> newChecked = new HashSet<>();
        for (int idx : checkedLaunchers) {
            if (idx < removed) newChecked.add(idx);
            else if (idx > removed) newChecked.add(idx - 1);
        }
        checkedLaunchers.clear();
        checkedLaunchers.addAll(newChecked);

        if (scrollOffset > 0 && scrollOffset >= launcherPositions.size() - MAX_VISIBLE_LAUNCHERS + 1) {
            scrollOffset--;
        }

        selectedLauncherIndex = -1;
        rebuildLauncherButtons();
        updateButtonStates();
        updateCheckAllButton();

        if (launcherPositions.isEmpty()) {
            this.onClose();
        }
    }

    private void connectCamera(int entityId) {
        NetworkHandler.sendToServer(new OrlanCameraViewPacket(entityId, true));
        OrlanCameraHandler.startCameraView(entityId);
        this.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        refreshTicks++;

        for (Map.Entry<Integer, Integer> entry : localTimerCache.entrySet()) {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
            }
        }

        if (refreshTicks % 20 == 0) {
            syncLocalTimers();
            rebuildCameraButtons();
            updateButtonStates();
        }
    }

    private void syncLocalTimers() {
        for (int[] orlanData : activeOrlans) {
            int entityId = orlanData[0];
            int serverTimer = getLiveTimerFromSource(entityId, orlanData);
            localTimerCache.put(entityId, serverTimer);
        }
    }

    private int getLiveTimerFromSource(int entityId, int[] fallbackData) {
        int[] cached = com.synarsis.airtacticalarsenal.network.ClientPacketHandler.getCachedOrlanData(entityId);
        if (cached != null) {
            return cached[1];
        }
        return fallbackData.length > 3 ? fallbackData[3] : 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int rightX = guiLeft + 195;

        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_COLOR);
        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 2, BORDER_COLOR);
        g.fill(guiLeft, guiTop + GUI_HEIGHT - 2, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BORDER_COLOR);
        g.fill(guiLeft, guiTop, guiLeft + 2, guiTop + GUI_HEIGHT, BORDER_COLOR);
        g.fill(guiLeft + GUI_WIDTH - 2, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BORDER_COLOR);

        String title = "ПЛАНШЕТ УПРАВЛЕНИЯ ОРЛАН-10";
        g.drawCenteredString(this.font, title, guiLeft + GUI_WIDTH / 2, guiTop + 8, ACCENT_COLOR);

        g.drawString(this.font, "ПУСКОВЫЕ УСТАНОВКИ: §8(Shift+ЛКМ)", guiLeft + 10, guiTop + 30, 0xAAAAAA, false);

        for (Button btn : launcherButtons) {
            btn.render(g, mouseX, mouseY, partialTick);
        }

        this.checkAllButton.render(g, mouseX, mouseY, partialTick);

        g.fill(guiLeft + 185, guiTop + 25, guiLeft + 187, guiTop + 185, BORDER_COLOR);

        if (selectedLauncherIndex >= 0 && selectedLauncherIndex < launcherPositions.size()) {
            BlockPos pos = launcherPositions.get(selectedLauncherIndex);
            boolean hasDrone = selectedLauncherIndex < hasDroneList.size() && hasDroneList.get(selectedLauncherIndex);
            boolean hasRoute = selectedLauncherIndex < savedRoutes.size() && !savedRoutes.get(selectedLauncherIndex).isEmpty();
            int routePts = hasRoute ? savedRoutes.get(selectedLauncherIndex).size() : 0;
            boolean inRange = isPlayerInRange(selectedLauncherIndex);

            int y = guiTop + 28;
            g.drawString(this.font, "§eВЫБРАНА: ПУ #" + (selectedLauncherIndex + 1), rightX, y, 0xFFFF00, false);
            y += 14;
            g.drawString(this.font, "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]", rightX, y, 0x888888, false);
            y += 16;

            g.drawString(this.font, hasDrone ? "§a● ДРОН ГОТОВ" : "§c○ НЕТ ДРОНА", rightX, y, 0xFFFFFF, false);
            y += 14;

            String routeText = hasRoute ? "§a✓ МАРШРУТ (" + routePts + " тч.)" : "§6✗ НЕТ МАРШРУТА";
            g.drawString(this.font, routeText, rightX, y, 0xFFFFFF, false);
            y += 14;

            if (!inRange) {
                g.drawString(this.font, "§c⚠ Слишком далеко от ПУ!", rightX, y, 0xFF4444, false);
                y += 11;
                g.drawString(this.font, "§7Макс. 30 блоков для запуска", rightX, y, 0x888888, false);
                y += 14;
            } else {
                y += 4;
            }

            if (!checkedLaunchers.isEmpty()) {
                int totalChecked = checkedLaunchers.size();
                int withDrone = 0, withRoute = 0;
                for (int idx : checkedLaunchers) {
                    if (idx < hasDroneList.size() && hasDroneList.get(idx)) withDrone++;
                    if (idx < savedRoutes.size() && !savedRoutes.get(idx).isEmpty()) withRoute++;
                }
                g.drawString(this.font, String.format("§fОтмечено: %d ПУ", totalChecked), rightX, y, 0xAAAAAA, false);
                y += 12;
                g.drawString(this.font, String.format("С дроном: %d | С маршр.: %d", withDrone, withRoute), rightX, y, 0x777777, false);
            }
        } else {
            g.drawString(this.font, "Выберите ПУ из списка", rightX, guiTop + 60, 0x666666, false);

            if (!checkedLaunchers.isEmpty()) {
                g.drawString(this.font, String.format("§fОтмечено: %d ПУ", checkedLaunchers.size()), rightX, guiTop + 80, 0xAAAAAA, false);
            }
        }

        this.routeButton.render(g, mouseX, mouseY, partialTick);
        this.removeButton.render(g, mouseX, mouseY, partialTick);
        this.launchButton.render(g, mouseX, mouseY, partialTick);

        g.fill(guiLeft + 5, guiTop + 190, guiLeft + GUI_WIDTH - 5, guiTop + 192, BORDER_COLOR);

        g.drawString(this.font, "§bАКТИВНЫЕ ДРОНЫ (" + activeOrlans.size() + "):", guiLeft + 10, guiTop + 196, ACCENT_COLOR, false);

        if (activeOrlans.isEmpty()) {
            g.drawString(this.font, "Нет активных Орланов", guiLeft + 30, guiTop + 215, 0x666666, false);
        }

        for (Button btn : cameraButtons) {
            btn.render(g, mouseX, mouseY, partialTick);
        }
        for (Button btn : routeUpdateButtons) {
            btn.render(g, mouseX, mouseY, partialTick);
        }
        for (Button btn : returnButtons) {
            btn.render(g, mouseX, mouseY, partialTick);
        }

        if (!statusMessage.isEmpty()) {
            g.drawCenteredString(this.font, statusMessage, guiLeft + GUI_WIDTH / 2, guiTop + GUI_HEIGHT - 15, 0xFFFFFF);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
