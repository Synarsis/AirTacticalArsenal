package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.network.LaunchShahedFromTerminalPacket;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ShahedTerminalScreen extends Screen {
    private static final int GUI_WIDTH = 200;
    private static final int GUI_HEIGHT = 220;
    private final BlockPos terminalPos;
    private final int launchCost;
    private final int targetY;
    private final boolean blacklistEnabled;
    private final boolean whitelistEnabled;
    private final List<String> blacklistZones;
    private final List<String> whitelistZones;
    private EditBox xCoordInput;
    private EditBox zCoordInput;
    private Button launchButton;
    private Button pasteWaypointButton;
    private Button saveWaypointButton;

    private EditBox routeNameField;
    private Button prevRouteBtn;
    private Button nextRouteBtn;
    private int selectedSavedRouteIndex = -1;
    private java.util.List<com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SavedRoute> cachedSavedRoutes = new java.util.ArrayList<>();

    private String errorMessage = "";
    private int playerCoins = 0;

    public ShahedTerminalScreen(BlockPos terminalPos, int launchCost, int targetY,
            boolean blacklistEnabled, boolean whitelistEnabled,
            List<String> blacklistZones, List<String> whitelistZones) {
        super(Component.translatable("gui.ata.terminal.title"));
        this.terminalPos = terminalPos;
        this.launchCost = launchCost;
        this.targetY = targetY;
        this.blacklistEnabled = blacklistEnabled;
        this.whitelistEnabled = whitelistEnabled;
        this.blacklistZones = blacklistZones;
        this.whitelistZones = whitelistZones;
    }

    @Override
    protected void init() {
        super.init();
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        com.synarsis.airtacticalarsenal.client.ClientWaypointManager.load();
        this.updateCoinCount();
        this.xCoordInput = new EditBox(this.font, guiLeft + 25, guiTop + 60, 75, 20,
                Component.translatable("gui.ata.terminal.coord_x"));
        this.xCoordInput.setMaxLength(10);
        this.xCoordInput.setValue("0");
        this.xCoordInput.setFilter(this::isValidNumber);
        this.addWidget(this.xCoordInput);

        cachedSavedRoutes = com.synarsis.airtacticalarsenal.client.ClientWaypointManager
                .getRoutes(com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SystemType.SHAHED);
        if (!cachedSavedRoutes.isEmpty()) {
            selectedSavedRouteIndex = 0;
        }

        int saveBlockY = guiTop + 120; 

        this.routeNameField = new EditBox(this.font, guiLeft + 25, saveBlockY, 60, 14, Component.literal("Имя цели"));
        this.routeNameField.setMaxLength(20);
        this.addWidget(this.routeNameField);

        if (selectedSavedRouteIndex >= 0 && selectedSavedRouteIndex < cachedSavedRoutes.size()) {
            this.routeNameField.setValue(cachedSavedRoutes.get(selectedSavedRouteIndex).name);
        }

        this.prevRouteBtn = Button.builder(Component.literal("<"), button -> cycleSavedRoute(-1))
                .bounds(guiLeft + 5, saveBlockY, 18, 14).build();
        this.addWidget(this.prevRouteBtn);

        this.nextRouteBtn = Button.builder(Component.literal(">"), button -> cycleSavedRoute(1))
                .bounds(guiLeft + 87, saveBlockY, 18, 14).build();
        this.addWidget(this.nextRouteBtn);

        this.pasteWaypointButton = Button.builder(Component.literal("ЗАГРУЗИТЬ"), button -> {
            if (selectedSavedRouteIndex >= 0 && selectedSavedRouteIndex < cachedSavedRoutes.size()) {
                var saved = cachedSavedRoutes.get(selectedSavedRouteIndex);
                if (saved.points != null && !saved.points.isEmpty()) {
                    BlockPos pt = saved.points.get(saved.points.size() - 1); 
                    this.xCoordInput.setValue(String.valueOf(pt.getX()));
                    this.zCoordInput.setValue(String.valueOf(pt.getZ()));
                }
            }
        }).bounds(guiLeft + 107, saveBlockY - 14, 80, 14).build();
        this.addWidget(this.pasteWaypointButton);

        this.saveWaypointButton = Button.builder(Component.literal("СОХРАНИТЬ К."), button -> {
            try {
                String n = routeNameField.getValue().trim();
                if (n.isEmpty())
                    return;
                int px = Integer.parseInt(this.xCoordInput.getValue());
                int pz = Integer.parseInt(this.zCoordInput.getValue());
                com.synarsis.airtacticalarsenal.client.ClientWaypointManager.saveRoute(
                        com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SystemType.SHAHED, n,
                        java.util.List.of(new BlockPos(px, this.targetY, pz)));

                cachedSavedRoutes = com.synarsis.airtacticalarsenal.client.ClientWaypointManager.getRoutes(
                        com.synarsis.airtacticalarsenal.client.ClientWaypointManager.SystemType.SHAHED);
                for (int i = 0; i < cachedSavedRoutes.size(); i++) {
                    if (cachedSavedRoutes.get(i).name.equals(n)) {
                        selectedSavedRouteIndex = i;
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }).bounds(guiLeft + 107, saveBlockY + 2, 80, 14).build();
        this.addWidget(this.saveWaypointButton);

        this.launchButton = Button
                .builder(Component.translatable("gui.ata.terminal.launch"), button -> this.onLaunchClicked())
                .bounds(guiLeft + 50, guiTop + 145, 100, 20)
                .build();
        this.launchButton.active = this.playerCoins >= this.launchCost;
        this.addWidget(this.launchButton);
    }

    private void cycleSavedRoute(int delta) {
        if (cachedSavedRoutes.isEmpty())
            return;
        selectedSavedRouteIndex = (selectedSavedRouteIndex + delta + cachedSavedRoutes.size())
                % cachedSavedRoutes.size();
        routeNameField.setValue(cachedSavedRoutes.get(selectedSavedRouteIndex).name);
    }

    private void updateCoinCount() {

    }

    private boolean isValidNumber(String input) {
        if (input.isEmpty()) {
            return true;
        }
        if (input.equals("-")) {
            return true;
        }
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void onLaunchClicked() {
        try {
            int targetX = Integer.parseInt(this.xCoordInput.getValue());
            int targetZ = Integer.parseInt(this.zCoordInput.getValue());
            if (this.isInForbiddenZone(targetX, targetZ)) {
                this.errorMessage = "§c" + Component.translatable("gui.ata.terminal.error.forbidden").getString();
                return;
            }
            NetworkHandler.sendToServer(new LaunchShahedFromTerminalPacket(targetX, targetZ, this.terminalPos));
            this.onClose();
        } catch (NumberFormatException e) {
            this.errorMessage = "§c" + Component.translatable("gui.ata.terminal.error.format").getString();
        }
    }

    private boolean isInForbiddenZone(int x, int z) {
        if (this.whitelistEnabled) {
            boolean inWhitelist = false;
            for (String zoneStr : this.whitelistZones) {
                if (zoneContains(zoneStr, x, z)) {
                    inWhitelist = true;
                    break;
                }
            }
            if (!inWhitelist) {
                return true;
            }
        }

        if (this.blacklistEnabled) {
            for (String zoneStr : this.blacklistZones) {
                if (zoneContains(zoneStr, x, z)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean zoneContains(String zoneStr, int x, int z) {
        try {
            String[] parts = zoneStr.split(",");
            if (parts.length == 4) {
                int x1 = Math.min(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[2].trim()));
                int z1 = Math.min(Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[3].trim()));
                int x2 = Math.max(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[2].trim()));
                int z2 = Math.max(Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[3].trim()));
                return x >= x1 && x <= x2 && z >= z1 && z <= z2;
            }
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        guiGraphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF1A1A1A);
        guiGraphics.fill(guiLeft + 2, guiTop + 2, guiLeft + GUI_WIDTH - 2, guiTop + GUI_HEIGHT - 2, 0xFF0D0D0D);
        guiGraphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 2, 0xFF00FF00);
        guiGraphics.fill(guiLeft, guiTop + GUI_HEIGHT - 2, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF00FF00);
        guiGraphics.fill(guiLeft, guiTop, guiLeft + 2, guiTop + GUI_HEIGHT, 0xFF00FF00);
        guiGraphics.fill(guiLeft + GUI_WIDTH - 2, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF00FF00);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        String title = Component.translatable("gui.ata.terminal.title").getString();
        int titleWidth = this.font.width(title);
        guiGraphics.drawString(this.font, title, guiLeft + (GUI_WIDTH - titleWidth) / 2, guiTop + 10, 0x00FF00, false);
        guiGraphics.fill(guiLeft + 10, guiTop + 25, guiLeft + GUI_WIDTH - 10, guiTop + 26, 0xFF00FF00);
        guiGraphics.drawString(this.font, Component.translatable("gui.ata.terminal.coord_x").getString(), guiLeft + 25,
                guiTop + 50, 0x00FF00, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.ata.terminal.coord_z").getString(), guiLeft + 25,
                guiTop + 85, 0x00FF00, false);
        guiGraphics.drawString(this.font, "Имя цели:", guiLeft + 25, guiTop + 110, 0x888888, false);
        String targetYInfo = Component.translatable("gui.ata.terminal.target_y", this.targetY).getString();
        guiGraphics.drawString(this.font, targetYInfo, guiLeft + 50, guiTop + 138, 0x888888, false);
        String costText = Component.translatable("gui.ata.terminal.cost", this.launchCost).getString();
        String balanceText = Component.translatable("gui.ata.terminal.balance", this.playerCoins).getString();
        int costColor = this.playerCoins >= this.launchCost ? 0xFFFF00 : 0xFF0000;
        guiGraphics.drawString(this.font, costText, guiLeft + 50, guiTop + 165, costColor, false);
        guiGraphics.drawString(this.font, balanceText, guiLeft + 50, guiTop + 175, 0x00FF00, false);
        if (!this.errorMessage.isEmpty()) {
            int errorWidth = this.font.width(this.errorMessage);
            guiGraphics.drawString(this.font, this.errorMessage, guiLeft + (GUI_WIDTH - errorWidth) / 2, guiTop + 195,
                    0xFF0000, false);
        }
        try {
            int targetX = Integer.parseInt(this.xCoordInput.getValue());
            int targetZ = Integer.parseInt(this.zCoordInput.getValue());
            if (this.isInForbiddenZone(targetX, targetZ)) {
                String warning = "§c" + Component.translatable("gui.ata.terminal.warning.forbidden").getString();
                int warningWidth = this.font.width(warning);
                guiGraphics.drawString(this.font, warning, guiLeft + (GUI_WIDTH - warningWidth) / 2, guiTop + 205,
                        0xFF0000, false);
            }
        } catch (NumberFormatException ignored) {
        }
        this.xCoordInput.render(guiGraphics, mouseX, mouseY, partialTick);
        this.zCoordInput.render(guiGraphics, mouseX, mouseY, partialTick);
        this.routeNameField.render(guiGraphics, mouseX, mouseY, partialTick);
        this.launchButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.pasteWaypointButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.saveWaypointButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.prevRouteBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        this.nextRouteBtn.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            double distance = player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(terminalPos));
            if (distance > 7.0) {
                this.onClose();
                return;
            }
        }

        this.xCoordInput.tick();
        this.zCoordInput.tick();
        this.updateCoinCount();
        this.launchButton.active = this.playerCoins >= this.launchCost;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
