package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.SetLauncherTargetPacket;
import com.synarsis.airtacticalarsenal.client.ClientWaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class TargetSettingsScreen extends Screen {

    private static final int GUI_WIDTH = 350;
    private static final int GUI_HEIGHT = 215;

    private final UnifiedTerminalScreen parentScreen;
    private final BlockPos launcherPos;
    private final BlockPos terminalPos;

    private EditBox xCoordInput;
    private EditBox zCoordInput;
    private EditBox routeNameField;

    private Button saveButton;
    private Button cancelButton;
    private Button saveWaypointButton;
    private Button deleteWaypointButton;

    private final Button[] routeButtons = new Button[5];
    private int scrollOffset = 0;
    private int selectedSavedRouteIndex = -1;

    private List<ClientWaypointManager.SavedRoute> cachedSavedRoutes = new ArrayList<>();

    private double calculatedDistance = 0;
    private double calculatedCEP = 0;
    private String errorMessage = "";

    public TargetSettingsScreen(UnifiedTerminalScreen parent, BlockPos launcherPos, BlockPos terminalPos) {
        super(Component.literal("Настройка цели"));
        this.parentScreen = parent;
        this.launcherPos = launcherPos;
        this.terminalPos = terminalPos;
    }

    @Override
    protected void init() {
        super.init();
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        BlockPos currentTarget = parentScreen.getLauncherTarget(launcherPos);
        String defaultX = currentTarget != null ? String.valueOf(currentTarget.getX()) : "0";
        String defaultZ = currentTarget != null ? String.valueOf(currentTarget.getZ()) : "0";

        ClientWaypointManager.load();
        cachedSavedRoutes = ClientWaypointManager.getRoutes(ClientWaypointManager.SystemType.ISKANDER);
        if (!cachedSavedRoutes.isEmpty()) {
            selectedSavedRouteIndex = 0;
        }

        for (int i = 0; i < 5; i++) {
            final int index = i;
            this.routeButtons[i] = Button.builder(Component.literal(""), b -> selectRoute(index))
                    .bounds(guiLeft + 10, guiTop + 33 + i * 22, 130, 20).build();
            this.addWidget(this.routeButtons[i]);
        }

        updateRouteButtons();

        int rightX = guiLeft + 160;

        this.xCoordInput = new EditBox(this.font, rightX + 65, guiTop + 40, 105, 18, Component.literal("X"));
        this.xCoordInput.setMaxLength(10);
        this.xCoordInput.setValue(defaultX);
        this.xCoordInput.setFilter(this::isValidNumber);
        this.xCoordInput.setResponder(value -> recalculateParameters());
        this.addWidget(this.xCoordInput);

        this.zCoordInput = new EditBox(this.font, rightX + 65, guiTop + 65, 105, 18, Component.literal("Z"));
        this.zCoordInput.setMaxLength(10);
        this.zCoordInput.setValue(defaultZ);
        this.zCoordInput.setFilter(this::isValidNumber);
        this.zCoordInput.setResponder(value -> recalculateParameters());
        this.addWidget(this.zCoordInput);

        this.routeNameField = new EditBox(this.font, rightX + 65, guiTop + 90, 105, 18, Component.literal("Имя цели"));
        this.routeNameField.setMaxLength(20);
        if (selectedSavedRouteIndex >= 0 && selectedSavedRouteIndex < cachedSavedRoutes.size()) {
            this.routeNameField.setValue(cachedSavedRoutes.get(selectedSavedRouteIndex).name);
        }
        this.addWidget(this.routeNameField);

        this.saveWaypointButton = Button.builder(Component.literal("СОХРАНИТЬ В СПИСОК"), b -> onSaveWaypoint())
                .bounds(rightX, guiTop + 115, 110, 20).build();
        this.addWidget(this.saveWaypointButton);

        this.deleteWaypointButton = Button.builder(Component.literal("УДАЛИТЬ"), b -> onDeleteWaypoint())
                .bounds(rightX + 115, guiTop + 115, 55, 20).build();
        this.addWidget(this.deleteWaypointButton);

        this.cancelButton = Button.builder(Component.literal("ОТМЕНА"), b -> onCancel())
                .bounds(rightX, guiTop + 180, 80, 20).build();
        this.addWidget(this.cancelButton);

        this.saveButton = Button.builder(Component.literal("ПРИМЕНИТЬ"), b -> onSave())
                .bounds(rightX + 90, guiTop + 180, 80, 20).build();
        this.addWidget(this.saveButton);

        recalculateParameters();
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
            ClientWaypointManager.SavedRoute route = cachedSavedRoutes.get(routeIndex);

            if (route.points != null && !route.points.isEmpty()) {
                BlockPos pt = route.points.get(route.points.size() - 1);
                this.xCoordInput.setValue(String.valueOf(pt.getX()));
                this.zCoordInput.setValue(String.valueOf(pt.getZ()));
                this.routeNameField.setValue(route.name);
                recalculateParameters();
            }
            updateRouteButtons();
        }
    }

    private void onSaveWaypoint() {
        try {
            String name = routeNameField.getValue().trim();
            if (name.isEmpty())
                return;

            int targetX = Integer.parseInt(this.xCoordInput.getValue());
            int targetZ = Integer.parseInt(this.zCoordInput.getValue());
            int targetY = ShahedConfig.TARGET_Y_LEVEL.get();

            ClientWaypointManager.saveRoute(
                    ClientWaypointManager.SystemType.ISKANDER,
                    name,
                    List.of(new BlockPos(targetX, targetY, targetZ)));

            cachedSavedRoutes = ClientWaypointManager.getRoutes(ClientWaypointManager.SystemType.ISKANDER);

            for (int i = 0; i < cachedSavedRoutes.size(); i++) {
                if (cachedSavedRoutes.get(i).name.equals(name)) {
                    selectedSavedRouteIndex = i;
                    if (selectedSavedRouteIndex < scrollOffset) {
                        scrollOffset = selectedSavedRouteIndex;
                    } else if (selectedSavedRouteIndex >= scrollOffset + 5) {
                        scrollOffset = selectedSavedRouteIndex - 4;
                    }
                    break;
                }
            }
            updateRouteButtons();
        } catch (NumberFormatException ignored) {
        }
    }

    private void onDeleteWaypoint() {
        if (selectedSavedRouteIndex >= 0 && selectedSavedRouteIndex < cachedSavedRoutes.size()) {
            String name = cachedSavedRoutes.get(selectedSavedRouteIndex).name;
            ClientWaypointManager.deleteRoute(ClientWaypointManager.SystemType.ISKANDER, name);

            cachedSavedRoutes = ClientWaypointManager.getRoutes(ClientWaypointManager.SystemType.ISKANDER);
            selectedSavedRouteIndex = -1;
            scrollOffset = 0;
            updateRouteButtons();

            this.xCoordInput.setValue("0");
            this.zCoordInput.setValue("0");
            this.routeNameField.setValue("");
            recalculateParameters();
        }
    }

    private boolean isValidNumber(String input) {
        if (input.isEmpty() || input.equals("-"))
            return true;
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void recalculateParameters() {
        try {
            int targetX = Integer.parseInt(this.xCoordInput.getValue());
            int targetZ = Integer.parseInt(this.zCoordInput.getValue());

            double dx = targetX - launcherPos.getX();
            double dz = targetZ - launcherPos.getZ();

            calculatedDistance = Math.sqrt(dx * dx + dz * dz);

            double baseCEP = 10.0;
            double distanceFactor = calculatedDistance / 100.0;
            calculatedCEP = Math.round(baseCEP + distanceFactor);

            int minDist = ShahedConfig.getIskanderMinDistance();
            int maxDist = ShahedConfig.getIskanderMaxRange();

            if (calculatedDistance < minDist) {
                errorMessage = "";
                if (saveButton != null)
                    saveButton.active = false;
            } else if (calculatedDistance > maxDist) {
                errorMessage = "";
                if (saveButton != null)
                    saveButton.active = false;
            } else {
                errorMessage = "";
                if (saveButton != null)
                    saveButton.active = true;
            }
        } catch (NumberFormatException e) {
            calculatedDistance = 0;
            calculatedCEP = 0;
            errorMessage = "§cНекорректные данные!";
            if (saveButton != null)
                saveButton.active = false;
        }
    }

    private void onCancel() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    private void onSave() {
        try {
            int targetX = Integer.parseInt(this.xCoordInput.getValue());
            int targetZ = Integer.parseInt(this.zCoordInput.getValue());
            int targetY = ShahedConfig.TARGET_Y_LEVEL.get();

            BlockPos target = new BlockPos(targetX, targetY, targetZ);

            NetworkHandler.sendToServer(new SetLauncherTargetPacket(launcherPos, target));
            parentScreen.updateLauncherTarget(launcherPos, target, calculatedDistance, calculatedCEP);
            Minecraft.getInstance().setScreen(parentScreen);

        } catch (NumberFormatException e) {
            errorMessage = "§cОшибка сохранения!";
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        guiGraphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF1A1A1A);
        guiGraphics.fill(guiLeft + 2, guiTop + 2, guiLeft + GUI_WIDTH - 2, guiTop + GUI_HEIGHT - 2, 0xFF0D0D0D);

        int borderColor = 0xFFFF6600;
        guiGraphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 2, borderColor);
        guiGraphics.fill(guiLeft, guiTop + GUI_HEIGHT - 2, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, borderColor);
        guiGraphics.fill(guiLeft, guiTop, guiLeft + 2, guiTop + GUI_HEIGHT, borderColor);
        guiGraphics.fill(guiLeft + GUI_WIDTH - 2, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, borderColor);

        guiGraphics.fill(guiLeft + 8, guiTop + 28, guiLeft + 142, guiTop + 30, 0xFF444444);
        guiGraphics.fill(guiLeft + 8, guiTop + 150, guiLeft + 142, guiTop + 152, 0xFF444444);
        guiGraphics.fill(guiLeft + 8, guiTop + 28, guiLeft + 10, guiTop + 152, 0xFF444444);
        guiGraphics.fill(guiLeft + 140, guiTop + 28, guiLeft + 142, guiTop + 152, 0xFF444444);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        String title = String.format("НАСТРОЙКА ЦЕЛИ [%d, %d, %d]",
                launcherPos.getX(), launcherPos.getY(), launcherPos.getZ());
        int titleWidth = this.font.width(title);
        guiGraphics.drawString(this.font, title, guiLeft + (GUI_WIDTH - titleWidth) / 2, guiTop + 10, borderColor,
                false);

        guiGraphics.fill(guiLeft + 10, guiTop + 22, guiLeft + GUI_WIDTH - 10, guiTop + 23, borderColor);

        int rightX = guiLeft + 160;
        guiGraphics.drawString(this.font, "Коорд. X:", rightX, guiTop + 45, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "Коорд. Z:", rightX, guiTop + 70, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, "Имя цели:", rightX, guiTop + 95, 0xAAAAAA, false);

        int sepY = guiTop + 142;
        guiGraphics.fill(rightX, sepY, guiLeft + GUI_WIDTH - 10, sepY + 1, 0xFF444444);

        int minDist = ShahedConfig.getIskanderMinDistance();
        int maxDist = ShahedConfig.getIskanderMaxRange();

        if (calculatedDistance > 0) {
            String distStr = String.format("Дист.: %.0f бл.", calculatedDistance);
            guiGraphics.drawString(this.font, distStr, rightX, sepY + 8, 0xAAAAAA, false);

            String cepStr = String.format("КВО: ±%.0f", calculatedCEP);
            guiGraphics.drawString(this.font, cepStr, rightX, sepY + 20, 0xAAAAAA, false);

            String rangeStr = String.format("Мин: %d Макс: %d", minDist, maxDist);
            int rangeColor = (calculatedDistance < minDist || calculatedDistance > maxDist) ? 0xFF5555 : 0x55FF55;
            guiGraphics.drawString(this.font, rangeStr, rightX + 65, sepY + 20, rangeColor, false);
        } else {
            guiGraphics.drawString(this.font, "Дист.: ----", rightX, sepY + 8, 0x888888, false);
            guiGraphics.drawString(this.font, "КВО: ----", rightX, sepY + 20, 0x888888, false);
        }

        this.xCoordInput.render(guiGraphics, mouseX, mouseY, partialTick);
        this.zCoordInput.render(guiGraphics, mouseX, mouseY, partialTick);
        this.routeNameField.render(guiGraphics, mouseX, mouseY, partialTick);
        this.saveWaypointButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.deleteWaypointButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.saveButton.render(guiGraphics, mouseX, mouseY, partialTick);
        for (Button b : routeButtons) {
            if (b.visible)
                b.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (!errorMessage.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, errorMessage, rightX + 85, guiTop + 165, 0xFF5555);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.xCoordInput.tick();
        this.zCoordInput.tick();
        this.routeNameField.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        if (mouseX >= guiLeft + 8 && mouseX <= guiLeft + 142 && mouseY >= guiTop + 28 && mouseY <= guiTop + 152) {
            if (delta > 0) {
                scroll(-1);
            } else if (delta < 0) {
                scroll(1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
