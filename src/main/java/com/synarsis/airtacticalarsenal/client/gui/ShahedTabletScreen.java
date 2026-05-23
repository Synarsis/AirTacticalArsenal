package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.network.LaunchShahedByRoutePacket;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.RemoveLauncherFromTabletPacket;
import com.synarsis.airtacticalarsenal.network.SaveTabletCoordsPacket;
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
public class ShahedTabletScreen extends Screen {

    private static final int GUI_WIDTH = 420;
    private static final int GUI_HEIGHT = 280;

    private static final int BORDER_COLOR = 0xFF006400;
    private static final int BG_COLOR = 0xE0101010;

    private final List<BlockPos> launcherPositions;
    private final List<Boolean> hasDroneList;
    private final List<BlockPos> targetList;
    private final List<Float> launcherYawList;
    private final Map<Integer, int[]> savedCoords = new HashMap<>();
    private final List<List<BlockPos>> savedRoutes;

    private int selectedLauncherIndex = -1;
    private final Set<Integer> checkedLaunchers = new HashSet<>();

    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_LAUNCHERS = 8;

    private Button launchButton;
    private Button launchAllButton;
    private Button removeButton;
    private Button routeButton;
    private Button checkAllButton;
    private final List<Button> launcherButtons = new ArrayList<>();

    private String statusMessage = "";

    public ShahedTabletScreen(List<BlockPos> launcherPositions, 
                               List<Boolean> hasDroneList,
                               List<BlockPos> targetList,
                               List<Float> launcherYawList,
                               Map<Integer, int[]> savedCoords,
                               List<List<BlockPos>> savedRoutes,
                               List<int[]> flyingShahedsData) {
        super(Component.literal(""));
        this.launcherPositions = new ArrayList<>(launcherPositions);
        this.hasDroneList = new ArrayList<>(hasDroneList);
        this.targetList = new ArrayList<>(targetList);
        this.launcherYawList = launcherYawList != null ? new ArrayList<>(launcherYawList) : new ArrayList<>();
        this.savedRoutes = savedRoutes != null ? new ArrayList<>(savedRoutes) : new ArrayList<>();
        if (savedCoords != null) {
            this.savedCoords.putAll(savedCoords);
        }
    }

    @Override
    protected void init() {
        super.init();

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int rightX = guiLeft + 195;
        int btnW = 100;

        rebuildLauncherButtons();

        this.checkAllButton = Button.builder(
                Component.literal(checkedLaunchers.size() == launcherPositions.size() ? "СНЯТЬ ВСЕ" : "ВЫБРАТЬ ВСЕ"),
                button -> toggleCheckAll())
            .bounds(guiLeft + 15, guiTop + GUI_HEIGHT - 30, 160, 18)
            .build();
        this.addWidget(this.checkAllButton);

        this.routeButton = Button.builder(
                Component.literal("МАРШРУТ"),
                button -> onRouteClicked())
            .bounds(rightX, guiTop + GUI_HEIGHT - 80, btnW, 20)
            .build();
        this.routeButton.active = false;
        this.addWidget(this.routeButton);

        this.removeButton = Button.builder(
                Component.literal("ОТВЯЗАТЬ"),
                button -> onRemoveClicked())
            .bounds(rightX + btnW + 5, guiTop + GUI_HEIGHT - 80, btnW, 20)
            .build();
        this.removeButton.active = false;
        this.addWidget(this.removeButton);

        this.launchButton = Button.builder(
                Component.literal("ЗАПУСК"),
                button -> onLaunchByRouteClicked())
            .bounds(rightX, guiTop + GUI_HEIGHT - 55, btnW, 20)
            .build();
        this.launchButton.active = false;
        this.addWidget(this.launchButton);

        this.launchAllButton = Button.builder(
                Component.literal("ЗАПУСК ВСЕХ"),
                button -> onLaunchAllClicked())
            .bounds(rightX + btnW + 5, guiTop + GUI_HEIGHT - 55, btnW, 20)
            .build();
        this.launchAllButton.active = false;
        this.addWidget(this.launchAllButton);

        updateButtonStates();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (launcherPositions.size() > MAX_VISIBLE_LAUNCHERS) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int)delta, launcherPositions.size() - MAX_VISIBLE_LAUNCHERS));
            rebuildLauncherButtons();
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
                .bounds(guiLeft + 10, guiTop + 42 + i * 26, 170, 22)
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

    private int tickCounter = 0;

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
        if (tickCounter % 20 == 0) {
            updateButtonStates();
        }
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedLauncherIndex >= 0 && selectedLauncherIndex < launcherPositions.size();
        boolean hasDrone = hasSelection && selectedLauncherIndex < hasDroneList.size() && hasDroneList.get(selectedLauncherIndex);
        boolean hasRoute = hasSelection && selectedLauncherIndex < savedRoutes.size() && !savedRoutes.get(selectedLauncherIndex).isEmpty();
        boolean inRange = hasSelection && isPlayerInRange(selectedLauncherIndex);

        this.launchButton.active = hasSelection && hasDrone && hasRoute && inRange;
        this.removeButton.active = hasSelection;

        this.routeButton.active = hasSelection || !checkedLaunchers.isEmpty();

        boolean anyReady = false;
        for (int i = 0; i < launcherPositions.size(); i++) {
            boolean d = i < hasDroneList.size() && hasDroneList.get(i);
            boolean r = i < savedRoutes.size() && !savedRoutes.get(i).isEmpty();
            if (d && r && isPlayerInRange(i)) { anyReady = true; break; }
        }
        this.launchAllButton.active = anyReady;

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
        Minecraft.getInstance().setScreen(new ShahedRouteScreen(this, positions, yaws, drones, routes,
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

    private void onLaunchByRouteClicked() {
        if (selectedLauncherIndex < 0 || selectedLauncherIndex >= launcherPositions.size()) return;
        BlockPos launcherPos = launcherPositions.get(selectedLauncherIndex);
        NetworkHandler.sendToServer(new LaunchShahedByRoutePacket(launcherPos, 0));

        if (selectedLauncherIndex < hasDroneList.size()) {
            hasDroneList.set(selectedLauncherIndex, false);
        }
        statusMessage = "§aЗапуск выполнен!";
        rebuildLauncherButtons();
        updateButtonStates();
    }

    private void onLaunchAllClicked() {
        List<BlockPos> readyLaunchers = new ArrayList<>();
        for (int i = 0; i < launcherPositions.size(); i++) {
            boolean hasDrone = i < hasDroneList.size() && hasDroneList.get(i);
            boolean hasRoute = i < savedRoutes.size() && !savedRoutes.get(i).isEmpty();
            if (hasDrone && hasRoute) {
                readyLaunchers.add(launcherPositions.get(i));
            }
        }
        if (readyLaunchers.isEmpty()) {
            statusMessage = "§cНет готовых ПУ!";
            return;
        }
        NetworkHandler.sendToServer(new LaunchShahedByRoutePacket(readyLaunchers, 0));

        for (int i = 0; i < launcherPositions.size(); i++) {
            boolean hasDrone = i < hasDroneList.size() && hasDroneList.get(i);
            boolean hasRoute = i < savedRoutes.size() && !savedRoutes.get(i).isEmpty();
            if (hasDrone && hasRoute && i < hasDroneList.size()) {
                hasDroneList.set(i, false);
            }
        }
        statusMessage = "§aЗапущено: " + readyLaunchers.size() + " ПУ";
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
        if (removed < targetList.size()) targetList.remove(removed);
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

    @Override
    public void onClose() {
        if (!savedCoords.isEmpty()) {
            NetworkHandler.sendToServer(new SaveTabletCoordsPacket(savedCoords));
        }
        super.onClose();
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

        String title = "ПЛАНШЕТ УПРАВЛЕНИЯ SHAHED";
        g.drawCenteredString(this.font, title, guiLeft + GUI_WIDTH / 2, guiTop + 8, BORDER_COLOR);

        renderLaunchersTab(g, mouseX, mouseY, partialTick, guiLeft, guiTop, rightX);

        if (!statusMessage.isEmpty()) {
            g.drawCenteredString(this.font, statusMessage, guiLeft + GUI_WIDTH / 2, guiTop + GUI_HEIGHT - 15, 0xFFFFFF);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderLaunchersTab(GuiGraphics g, int mouseX, int mouseY, float partialTick, int guiLeft, int guiTop, int rightX) {
        for (Button btn : launcherButtons) {
            btn.render(g, mouseX, mouseY, partialTick);
        }

        this.checkAllButton.render(g, mouseX, mouseY, partialTick);

        g.fill(guiLeft + 185, guiTop + 25, guiLeft + 187, guiTop + GUI_HEIGHT - 10, BORDER_COLOR);

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
                int totalChecked = checkedLaunchers.size();
                g.drawString(this.font, String.format("§fОтмечено: %d ПУ", totalChecked), rightX, guiTop + 80, 0xAAAAAA, false);
            }
        }

        this.routeButton.render(g, mouseX, mouseY, partialTick);
        this.removeButton.render(g, mouseX, mouseY, partialTick);
        this.launchButton.render(g, mouseX, mouseY, partialTick);
        this.launchAllButton.render(g, mouseX, mouseY, partialTick);

    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
