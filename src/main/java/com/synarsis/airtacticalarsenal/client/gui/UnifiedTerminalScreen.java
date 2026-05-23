package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.network.LaunchRocketPacket;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class UnifiedTerminalScreen extends Screen {

    public enum LauncherState {
        EMPTY,      
        LOADED,     
        COUNTDOWN,  
        IN_FLIGHT   
    }
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 320;
    private static final int ROCKET_PREP_TICKS = 140;

    private final BlockPos terminalPos;
    private final List<BlockPos> launcherPositions;
    private final List<Boolean> launcherLoadedStates;
    private final boolean hasLauncher;
    private final int launcherCount;
    private final int freeLaunchers;
    private final int launchCostShahed;
    private final int launchCostIskander;
    private final int targetY;
    private final boolean blacklistEnabled;
    private final boolean whitelistEnabled;
    private final List<String> blacklistZones;
    private final List<String> whitelistZones;

    private List<Button> launchButtons = new ArrayList<>();
    private List<Button> targetButtons = new ArrayList<>(); 
    private final List<Boolean> serverLaunchingStates; 

    private final Map<BlockPos, BlockPos> launcherTargets = new HashMap<>();
    private final Map<BlockPos, Double> launcherDistances = new HashMap<>();
    private final Map<BlockPos, Double> launcherCEPs = new HashMap<>();

    private final Map<BlockPos, LauncherState> launcherStateMap = new HashMap<>();
    private final Map<BlockPos, int[]> launcherTimersMap = new HashMap<>(); 

    private int selectedLauncherIndex = -1;
    private String errorMessage = "";
    private int playerCoins = 0;
    private int playerRockets = 0;

    private int scrollOffset = 0;

    public UnifiedTerminalScreen(BlockPos terminalPos, List<BlockPos> launcherPositions, List<Boolean> loadedStates,
                                 List<Boolean> launchingStates,
                                 boolean hasLauncher, int launcherCount, int freeLaunchers, 
                                 int launchCostShahed, int launchCostIskander, int targetY,
                                 boolean blacklistEnabled, boolean whitelistEnabled,
                                 List<String> blacklistZones, List<String> whitelistZones) {
        super(Component.translatable("gui.ata.unified_terminal.title"));
        this.terminalPos = terminalPos;
        this.launcherPositions = launcherPositions;
        this.hasLauncher = hasLauncher;
        this.launcherCount = launcherCount;
        this.freeLaunchers = freeLaunchers;
        this.launchCostShahed = launchCostShahed;
        this.launchCostIskander = launchCostIskander;
        this.targetY = targetY;
        this.blacklistEnabled = blacklistEnabled;
        this.whitelistEnabled = whitelistEnabled;
        this.blacklistZones = blacklistZones;
        this.whitelistZones = whitelistZones;

        this.launcherLoadedStates = new ArrayList<>();
        if (loadedStates != null) {
            this.launcherLoadedStates.addAll(loadedStates);
        }
        while (this.launcherLoadedStates.size() < launcherPositions.size()) {
            this.launcherLoadedStates.add(false);
        }

        this.serverLaunchingStates = new ArrayList<>();
        if (launchingStates != null) {
            this.serverLaunchingStates.addAll(launchingStates);
        }
        while (this.serverLaunchingStates.size() < launcherPositions.size()) {
            this.serverLaunchingStates.add(false);
        }
    }

    public void updateLauncherStates(List<Boolean> states) {
        this.launcherLoadedStates.clear();
        this.launcherLoadedStates.addAll(states);
    }

    public void updateLauncherState(BlockPos launcherPos, 
                                     com.synarsis.airtacticalarsenal.network.LauncherStateUpdatePacket.LauncherState state,
                                     int countdownTicks, int totalCountdownTicks, int flightTicks) {

        LauncherState localState = switch (state) {
            case EMPTY -> LauncherState.EMPTY;
            case LOADED -> LauncherState.LOADED;
            case COUNTDOWN -> LauncherState.COUNTDOWN;
            case IN_FLIGHT -> LauncherState.IN_FLIGHT;
        };

        launcherStateMap.put(launcherPos, localState);
        launcherTimersMap.put(launcherPos, new int[]{countdownTicks, totalCountdownTicks, flightTicks});

        for (int i = 0; i < launcherPositions.size(); i++) {
            if (launcherPositions.get(i).equals(launcherPos)) {
                boolean isLoaded = (localState == LauncherState.LOADED || localState == LauncherState.COUNTDOWN);
                boolean isLaunching = (localState == LauncherState.COUNTDOWN || localState == LauncherState.IN_FLIGHT);

                if (i < launcherLoadedStates.size()) {
                    launcherLoadedStates.set(i, isLoaded);
                }
                if (i < serverLaunchingStates.size()) {
                    serverLaunchingStates.set(i, isLaunching);
                }
                break;
            }
        }

        rebuildLauncherButtons();
        updateButtonStates();
    }

    private LauncherState getLauncherState(int launcherIndex) {
        if (launcherIndex < 0 || launcherIndex >= launcherPositions.size()) {
            return LauncherState.EMPTY;
        }

        BlockPos pos = launcherPositions.get(launcherIndex);

        if (launcherStateMap.containsKey(pos)) {
            return launcherStateMap.get(pos);
        }

        boolean isLoaded = launcherIndex < launcherLoadedStates.size() && launcherLoadedStates.get(launcherIndex);
        boolean isLaunching = launcherIndex < serverLaunchingStates.size() && serverLaunchingStates.get(launcherIndex);

        if (isLaunching) {
            return LauncherState.IN_FLIGHT;
        } else if (isLoaded) {
            return LauncherState.LOADED;
        } else {
            return LauncherState.EMPTY;
        }
    }

    private int[] getLauncherTimers(int launcherIndex) {
        if (launcherIndex < 0 || launcherIndex >= launcherPositions.size()) {
            return new int[]{0, 0, 0};
        }
        BlockPos pos = launcherPositions.get(launcherIndex);
        return launcherTimersMap.getOrDefault(pos, new int[]{0, 0, 0});
    }

    @Override
    protected void init() {
        super.init();
        this.updateCoinCount();

        rebuildLauncherButtons();
        updateButtonStates();
    }

    private void rebuildLauncherButtons() {
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        for (Button btn : launchButtons) {
            this.removeWidget(btn);
        }
        for (Button btn : targetButtons) {
            this.removeWidget(btn);
        }
        launchButtons.clear();
        targetButtons.clear();

        int startY = guiTop + 90;
        int entryHeight = 50; 
        int maxVisible = 4; 

        for (int i = 0; i < Math.min(launcherPositions.size(), maxVisible); i++) {
            int idx = i + scrollOffset;
            if (idx >= launcherPositions.size()) break;

            final int launcherIdx = idx;
            final BlockPos launcherPos = launcherPositions.get(idx);
            int y = startY + i * entryHeight;

            LauncherState state = getLauncherState(idx);

            int buttonRightEdge = guiLeft + GUI_WIDTH - 20;
            int launchBtnWidth = 50;
            int targetBtnWidth = 65;
            int buttonSpacing = 5;

            int buttonY = y + 3;

            boolean showButtons = (state == LauncherState.EMPTY || state == LauncherState.LOADED);

            Button targetBtn = Button.builder(Component.literal("ЦЕЛЬ"),
                    button -> openTargetSettings(launcherIdx))
                    .bounds(buttonRightEdge - targetBtnWidth, buttonY, targetBtnWidth, 16)
                    .build();
            targetBtn.active = showButtons;
            targetBtn.visible = showButtons;
            this.addWidget(targetBtn);
            targetButtons.add(targetBtn);

            boolean hasTarget = launcherTargets.containsKey(launcherPos);
            boolean canLaunch = state == LauncherState.LOADED && hasTarget;

            Button launchBtn = Button.builder(Component.literal("ПУСК"),
                    button -> onLaunchRocket(launcherIdx))
                    .bounds(buttonRightEdge - targetBtnWidth - buttonSpacing - launchBtnWidth, buttonY, launchBtnWidth, 16)
                    .build();
            launchBtn.active = canLaunch;
            launchBtn.visible = showButtons && state == LauncherState.LOADED;
            this.addWidget(launchBtn);
            launchButtons.add(launchBtn);
        }
    }

    private void openTargetSettings(int launcherIndex) {
        if (launcherIndex < 0 || launcherIndex >= launcherPositions.size()) return;
        BlockPos launcherPos = launcherPositions.get(launcherIndex);

        Minecraft.getInstance().setScreen(new TargetSettingsScreen(this, launcherPos, terminalPos));
    }

    public void updateLauncherTarget(BlockPos launcherPos, BlockPos target, double distance, double cep) {
        launcherTargets.put(launcherPos, target);
        launcherDistances.put(launcherPos, distance);
        launcherCEPs.put(launcherPos, cep);
        rebuildLauncherButtons();
        updateButtonStates();
    }

    public BlockPos getLauncherTarget(BlockPos launcherPos) {
        return launcherTargets.get(launcherPos);
    }

    public void loadTargetsFromServer(List<BlockPos> launcherPositions, 
                                       List<BlockPos> targets, 
                                       List<Double> distances, 
                                       List<Double> ceps) {
        if (targets == null || distances == null || ceps == null) return;

        for (int i = 0; i < launcherPositions.size() && i < targets.size(); i++) {
            BlockPos launcherPos = launcherPositions.get(i);
            BlockPos target = targets.get(i);

            if (target != null) {
                launcherTargets.put(launcherPos, target);
                launcherDistances.put(launcherPos, distances.get(i));
                launcherCEPs.put(launcherPos, ceps.get(i));
            }
        }
    }

    private void updateButtonStates() {
        for (int i = 0; i < launchButtons.size(); i++) {
            int idx = i + scrollOffset;
            if (idx >= launcherPositions.size()) continue;

            BlockPos launcherPos = launcherPositions.get(idx);
            LauncherState state = getLauncherState(idx);
            boolean showButtons = (state == LauncherState.EMPTY || state == LauncherState.LOADED);
            boolean hasTarget = launcherTargets.containsKey(launcherPos);
            boolean canLaunch = state == LauncherState.LOADED && hasTarget;

            launchButtons.get(i).active = canLaunch;
            launchButtons.get(i).visible = showButtons && state == LauncherState.LOADED;
        }
        for (int i = 0; i < targetButtons.size(); i++) {
            int idx = i + scrollOffset;
            LauncherState state = getLauncherState(idx);
            boolean showButtons = (state == LauncherState.EMPTY || state == LauncherState.LOADED);

            targetButtons.get(i).active = showButtons;
            targetButtons.get(i).visible = showButtons;
        }
    }

    private void updateCoinCount() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            this.playerCoins = 0;
            this.playerRockets = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(ModItems.ISKANDER.get())) {
                    this.playerRockets += stack.getCount();
                }
            }
        }
    }

    private void onLaunchRocket(int launcherIndex) {
        if (launcherIndex < 0 || launcherIndex >= launcherPositions.size()) return;

        BlockPos launcherPos = launcherPositions.get(launcherIndex);

        BlockPos target = launcherTargets.get(launcherPos);
        if (target == null) {
            this.errorMessage = "§cЦель не задана! Нажмите ЦЕЛЬ для настройки.";
            return;
        }

        int targetX = target.getX();
        int targetZ = target.getZ();

        if (this.isInForbiddenZone(targetX, targetZ)) {
            this.errorMessage = "§c" + Component.translatable("gui.ata.terminal.error.forbidden").getString();
            return;
        }

        double dist = Math.sqrt(Math.pow(targetX - launcherPos.getX(), 2) + Math.pow(targetZ - launcherPos.getZ(), 2));
        int minDist = ShahedConfig.getIskanderMinDistance();
        int maxDist = ShahedConfig.getIskanderMaxRange();

        if (dist < minDist) {
            this.errorMessage = "§c" + Component.translatable("gui.ata.unified_terminal.error.min_distance", minDist).getString();
            return;
        }
        if (dist > maxDist) {
            this.errorMessage = "§c" + Component.translatable("gui.ata.unified_terminal.error.max_distance", maxDist).getString();
            return;
        }

        NetworkHandler.sendToServer(new LaunchRocketPacket(launcherPos, terminalPos, targetX, targetZ));

        launcherStateMap.put(launcherPos, LauncherState.COUNTDOWN);
        launcherTimersMap.put(launcherPos, new int[]{0, ROCKET_PREP_TICKS, 0});

        if (launcherIndex < serverLaunchingStates.size()) {
            serverLaunchingStates.set(launcherIndex, true);
        }

        rebuildLauncherButtons();
        updateButtonStates();
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
            if (!inWhitelist) return true;
        }

        if (this.blacklistEnabled) {
            for (String zoneStr : this.blacklistZones) {
                if (zoneContains(zoneStr, x, z)) return true;
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
        } catch (NumberFormatException ignored) {}
        return false;
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

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        String title = "ТЕРМИНАЛ УПРАВЛЕНИЯ ИСКАНДЕР-М";
        int titleWidth = this.font.width(title);
        guiGraphics.drawString(this.font, title, guiLeft + (GUI_WIDTH - titleWidth) / 2, guiTop + 10, borderColor, false);

        guiGraphics.fill(guiLeft + 10, guiTop + 28, guiLeft + GUI_WIDTH - 10, guiTop + 29, borderColor);

        double cruiseSpeed = ShahedConfig.getIskanderCruiseSpeed() * 20;
        String speedStr = String.format("Скорость ракеты: %.1f бл/с", cruiseSpeed);
        guiGraphics.drawString(this.font, speedStr, guiLeft + 20, guiTop + 38, 0xAAAAAA, false);

        int minDist = ShahedConfig.getIskanderMinDistance();
        int maxDist = ShahedConfig.getIskanderMaxRange();
        String rangeStr = String.format("Дальность: %d - %d блоков", minDist, maxDist);
        guiGraphics.drawString(this.font, rangeStr, guiLeft + 20, guiTop + 50, 0xAAAAAA, false);

        guiGraphics.fill(guiLeft + 10, guiTop + 65, guiLeft + GUI_WIDTH - 10, guiTop + 66, 0xFF444444);

        {

            int totalLaunchers = launcherPositions.size();
            int loadedCount = 0;
            for (Boolean loaded : launcherLoadedStates) {
                if (loaded) loadedCount++;
            }
            int freeLaunchersCount = totalLaunchers - loadedCount;

            String launcherTitle = "Пусковые установки:";
            guiGraphics.drawString(this.font, launcherTitle, guiLeft + 20, guiTop + 72, 0xFFAA00, false);

            String stats = String.format("Всего: %d | Занято: %d | Свободно: %d", 
                totalLaunchers, loadedCount, freeLaunchersCount);
            guiGraphics.drawString(this.font, stats, guiLeft + 130, guiTop + 72, 0x55FF55, false);

            int startY = guiTop + 90;
            int entryHeight = 50; 
            int maxVisible = 4; 

            for (int i = 0; i < Math.min(launcherPositions.size(), maxVisible); i++) {
                int idx = i + scrollOffset;
                if (idx >= launcherPositions.size()) break;

                BlockPos pos = launcherPositions.get(idx);

                int y = startY + i * entryHeight;

                guiGraphics.fill(guiLeft + 15, y, guiLeft + GUI_WIDTH - 15, y + entryHeight - 4, 0xFF222222);

                String posStr = String.format("ПУ #%d [%d, %d, %d]", idx + 1, pos.getX(), pos.getY(), pos.getZ());
                guiGraphics.drawString(this.font, posStr, guiLeft + 20, y + 3, 0xCCCCCC, false);

                LauncherState state = getLauncherState(idx);
                int[] timers = getLauncherTimers(idx);

                String status;
                int statusColor;

                switch (state) {
                    case COUNTDOWN -> {
                        int remainingTicks = timers[1] - timers[0];
                        int remainingSeconds = (int) Math.ceil(remainingTicks / 20.0);
                        status = String.format("▶ Старт через: %d сек", remainingSeconds);
                        statusColor = 0xFFFF00;
                    }
                    case IN_FLIGHT -> {
                        int flightSeconds = timers[2] / 20;
                        status = String.format("▶ В полёте: %d сек", flightSeconds);
                        statusColor = 0xFFA500;
                    }
                    case LOADED -> {
                        status = "► ЗАРЯЖЕНО: Искандер-М";
                        statusColor = 0x55FF55;
                    }
                    default -> {
                        status = "► ПУСТО";
                        statusColor = 0xFF5555;
                    }
                }
                guiGraphics.drawString(this.font, status, guiLeft + 20, y + 14, statusColor, false);

                String targetText;
                if (state == LauncherState.EMPTY || state == LauncherState.LOADED) {
                    BlockPos target = launcherTargets.get(pos);
                    if (target != null) {
                        targetText = String.format("Цель: [X: %d, Z: %d]", target.getX(), target.getZ());
                    } else {
                        targetText = "Цель: не задана";
                    }
                } else {
                    targetText = "Цель: в полёте";
                }
                guiGraphics.drawString(this.font, targetText, guiLeft + 20, y + 25, 0xAAAAAA, false);

                String distText;
                if (state == LauncherState.EMPTY || state == LauncherState.LOADED) {
                    BlockPos target = launcherTargets.get(pos);
                    if (target != null) {
                        Double dist = launcherDistances.get(pos);
                        distText = String.format("Дистанция: %.0f блоков", dist != null ? dist : 0.0);
                    } else {
                        distText = "Дистанция: -";
                    }
                } else {
                    distText = "Дистанция: ---";
                }
                guiGraphics.drawString(this.font, distText, guiLeft + 20, y + 36, 0xAAAAAA, false);
            }

            if (launcherPositions.isEmpty()) {
                guiGraphics.drawString(this.font, "Нет подключенных ПУ", guiLeft + 20, startY + 10, 0xFF5555, false);
            }
        }

        if (!this.errorMessage.isEmpty()) {
            int errorWidth = this.font.width(this.errorMessage);
            guiGraphics.drawString(this.font, this.errorMessage, guiLeft + (GUI_WIDTH - errorWidth) / 2, guiTop + 305, 0xFF0000, false);
        }

        for (Button btn : launchButtons) {
            btn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        for (Button btn : targetButtons) {
            btn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {

        if (launcherPositions.size() > 4) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int)delta, launcherPositions.size() - 4));
            rebuildLauncherButtons();
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

        this.updateCoinCount();
        this.updateButtonStates();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
