package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.radar.TerrainMapRenderer;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.RadarUpdatePacket;
import com.synarsis.airtacticalarsenal.network.RequestRadarMapPacket;
import com.synarsis.airtacticalarsenal.network.RequestRadarUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ShahedRadarScreen extends Screen {

    private final BlockPos radarPos;
    private static final int DRONE_DETECT_RADIUS = 1500;
    private static final int UPDATE_INTERVAL = 20;

    private static final int[] ZOOM_LEVELS = {1, 2, 4, 8};
    private int zoomIndex = 2;

    private static ShahedRadarScreen currentInstance = null;

    private final Map<Integer, RadarTarget> targets = new ConcurrentHashMap<>();
    private int selectedTargetId = -1;
    private int hoveredTargetId = -1;
    private int tickCounter = 0;
    private float scanAngle = 0;

    private float panWorldX = 0;
    private float panWorldZ = 0;
    private boolean isDragging = false;
    private int targetScrollOffset = 0;

    private TerrainMapRenderer terrainMap;

    private static final int COL_BG         = 0xFF0a0e14;
    private static final int COL_FRAME      = 0xFF1c2a38;
    private static final int COL_FRAME_LITE = 0xFF2a3e52;
    private static final int COL_GRID       = 0x20408060;
    private static final int COL_CROSS      = 0x3060c090;
    private static final int COL_SWEEP      = 0x9030ff70;
    private static final int COL_RANGE_RING = 0xA0206040;
    private static final int COL_RADAR_DOT  = 0xFF30ff90;
    private static final int COL_TEXT       = 0xFFc0d0c0;
    private static final int COL_TEXT_DIM   = 0xFF607060;
    private static final int COL_TITLE      = 0xFF40ff80;
    private static final int COL_TARGET     = 0xFFe04040;
    private static final int COL_LABEL      = 0xFFffcc40;
    private static final int COL_SELECTED   = 0xFFffff60;
    private static final int COL_STATUS_BG  = 0xE0080c14;
    private static final int COL_STATUS_TXT = 0xFF40e080;
    private static final int COL_COMPASS    = 0xFF508868;
    private static final int COL_SW_HELI    = 0xFF40c0ff;
    private static final int COL_SW_PLANE   = 0xFFff8040;
    private static final int COL_SW_DRONE   = 0xFFc040ff;

    private static class RadarTarget {
        final int entityId;
        byte type;
        Vec3 position;
        Vec3 smoothPos;
        float heading;
        float speed;
        int staleTicks = 0;
        final List<Vec3> trail = new ArrayList<>();
        static final int MAX_TRAIL = 4;
        static final int STALE_REMOVE = 80;

        RadarTarget(int entityId, byte type) {
            this.entityId = entityId;
            this.type = type;
        }

        void update(Vec3 pos, float heading, float speed) {
            if (this.position != null && this.position.distanceToSqr(pos) > 1.0) {
                trail.add(this.position);
                if (trail.size() > MAX_TRAIL) trail.remove(0);
            }
            this.position = pos;
            if (this.smoothPos == null) this.smoothPos = pos;
            this.heading = heading;
            this.speed = speed;
            this.staleTicks = 0;
        }

        void interpolate(float factor) {
            if (position != null && smoothPos != null) {
                smoothPos = smoothPos.add(position.subtract(smoothPos).scale(factor));
            }
        }
    }

    public ShahedRadarScreen(BlockPos radarPos) {
        super(Component.translatable("gui.ata.radar.title"));
        this.radarPos = radarPos;
    }

    public static void open(BlockPos pos) {
        if (currentInstance != null) {
            currentInstance.closeTerrainMap();
        }
        ShahedRadarScreen screen = new ShahedRadarScreen(pos);
        currentInstance = screen;
        Minecraft.getInstance().setScreen(screen);
    }

    public static void updateTargets(BlockPos radarPos, List<RadarUpdatePacket.TargetData> data) {
        if (currentInstance == null || !currentInstance.radarPos.equals(radarPos)) return;
        for (RadarUpdatePacket.TargetData td : data) {
            RadarTarget t = currentInstance.targets.computeIfAbsent(
                    td.entityId, id -> new RadarTarget(id, td.type));
            t.update(td.pos, td.heading, td.speed);
            t.type = td.type;
        }
    }

    @Override
    protected void init() {
        super.init();
        if (terrainMap == null) {
            terrainMap = new TerrainMapRenderer(radarPos.getX(), radarPos.getZ());
        }

        if (terrainMap != null && !terrainMap.hasCacheData()) {
            NetworkHandler.sendToServer(new RequestRadarMapPacket(radarPos));
        }
        requestRadarUpdate();
    }

    @Override
    public void removed() {
        super.removed();
        closeTerrainMap();
        currentInstance = null;
    }

    private void closeTerrainMap() {
        if (terrainMap != null) {
            terrainMap.close();
            terrainMap = null;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (Minecraft.getInstance().player != null) {
            double distance = Minecraft.getInstance().player.position()
                    .distanceTo(Vec3.atCenterOf(radarPos));
            if (distance > 7.0) {
                this.onClose();
                return;
            }
        }

        tickCounter++;
        scanAngle += 1.8f;
        if (scanAngle >= 360) scanAngle -= 360;

        targets.values().forEach(t -> t.staleTicks++);
        targets.values().removeIf(t -> t.staleTicks > RadarTarget.STALE_REMOVE);
        if (selectedTargetId >= 0 && !targets.containsKey(selectedTargetId)) {
            selectedTargetId = -1;
        }

        if (tickCounter >= UPDATE_INTERVAL) {
            tickCounter = 0;
            requestRadarUpdate();
            if (terrainMap != null && !terrainMap.hasCacheData()) {
                NetworkHandler.sendToServer(new RequestRadarMapPacket(radarPos));
            }
        }
    }

    private void requestRadarUpdate() {
        NetworkHandler.sendToServer(new RequestRadarUpdatePacket(radarPos));
    }

    private static final int RIGHT_PANEL_W = 185;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);

        targets.values().forEach(t -> t.interpolate(0.25f));

        int panelSpace = RIGHT_PANEL_W + 6;
        int cx = (this.width - panelSpace) / 2;
        int cy = this.height / 2;
        int mapSide = Math.min(this.width - panelSpace, this.height) - 50;

        int half = mapSide / 2;

        renderFrame(g, cx, cy, half);
        renderTerrainMap(g, cx, cy, half);

        g.enableScissor(cx - half, cy - half, cx + half, cy + half);
        renderCrosshair(g, cx, cy, half);
        renderRangeRings(g, cx, cy, half);
        renderSweep(g, cx, cy, half);
        hoveredTargetId = -1;
        renderTargets(g, cx, cy, half, mouseX, mouseY);
        renderRadarMarker(g, cx, cy, half);
        renderCompass(g, cx, cy, half);
        g.disableScissor();
        renderHUD(g, cx, cy, half);
        renderStatusBar(g, cx, cy, half);
        renderRightPanel(g, cx, cy, half, mouseX, mouseY);

        super.render(g, mouseX, mouseY, pt);
    }

    private void renderFrame(GuiGraphics g, int cx, int cy, int half) {
        g.fill(cx - half - 3, cy - half - 3, cx + half + 3, cy + half + 3, COL_FRAME);
        g.fill(cx - half - 2, cy - half - 2, cx + half + 2, cy + half + 2, COL_FRAME_LITE);
        g.fill(cx - half, cy - half, cx + half, cy + half, COL_BG);
    }

    private void renderTerrainMap(GuiGraphics g, int cx, int cy, int half) {
        if (terrainMap == null || !terrainMap.isReady()) return;
        if (!terrainMap.hasCacheData()) {

            String loadText;
            if (terrainMap.isQueued()) {
                loadText = "\u0412 \u043e\u0447\u0435\u0440\u0435\u0434\u0438: #" + terrainMap.getQueuePosition();
            } else if (terrainMap.isBuilding()) {
                int pct = (int)(terrainMap.getBuildProgress() * 100);
                loadText = "\u0421\u043a\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435: " + pct + "%";
            } else {
                loadText = "\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430 \u043a\u0430\u0440\u0442\u044b...";
            }
            int tw = font.width(loadText);
            g.drawString(font, loadText, cx - tw / 2, cy - 4, 0xFF40FF80, false);
            return;
        }

        int texSize = terrainMap.getTextureSize();
        int bpp = ZOOM_LEVELS[zoomIndex];
        double viewCenterX = radarPos.getX() + panWorldX;
        double viewCenterZ = radarPos.getZ() + panWorldZ;
        double viewRadius = (double) half * bpp;

        double panDeltaX = viewCenterX - terrainMap.getRadarX();
        double panDeltaZ = viewCenterZ - terrainMap.getRadarZ();

        float srcLeft = (float)(texSize / 2.0 + panDeltaX / TerrainMapRenderer.CACHE_BPP - viewRadius / TerrainMapRenderer.CACHE_BPP);
        float srcTop = (float)(texSize / 2.0 + panDeltaZ / TerrainMapRenderer.CACHE_BPP - viewRadius / TerrainMapRenderer.CACHE_BPP);
        float srcRight = (float)(texSize / 2.0 + panDeltaX / TerrainMapRenderer.CACHE_BPP + viewRadius / TerrainMapRenderer.CACHE_BPP);
        float srcBottom = (float)(texSize / 2.0 + panDeltaZ / TerrainMapRenderer.CACHE_BPP + viewRadius / TerrainMapRenderer.CACHE_BPP);

        int side = half * 2;
        int dstLeft = cx - half;
        int dstTop = cy - half;
        int dstRight = cx + half;
        int dstBottom = cy + half;

        float fullSrcW = srcRight - srcLeft;
        float fullSrcH = srcBottom - srcTop;
        if (fullSrcW <= 0 || fullSrcH <= 0) return;

        if (srcLeft < 0) { dstLeft += (int)(-srcLeft / fullSrcW * side); srcLeft = 0; }
        if (srcTop < 0) { dstTop += (int)(-srcTop / fullSrcH * side); srcTop = 0; }
        if (srcRight > texSize) { dstRight -= (int)((srcRight - texSize) / fullSrcW * side); srcRight = texSize; }
        if (srcBottom > texSize) { dstBottom -= (int)((srcBottom - texSize) / fullSrcH * side); srcBottom = texSize; }

        int dstW = dstRight - dstLeft;
        int dstH = dstBottom - dstTop;
        int srcW = Math.max(1, (int)(srcRight - srcLeft));
        int srcH = Math.max(1, (int)(srcBottom - srcTop));

        if (dstW > 0 && dstH > 0) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, terrainMap.getTextureLocation());
            g.blit(terrainMap.getTextureLocation(),
                    dstLeft, dstTop, dstW, dstH,
                    srcLeft, srcTop, srcW, srcH,
                    texSize, texSize);
        }
    }

    private void renderCrosshair(GuiGraphics g, int cx, int cy, int half) {
        g.hLine(cx - half, cx + half, cy, COL_CROSS);
        g.vLine(cx, cy - half, cy + half, COL_CROSS);
    }

    private void renderRangeRings(GuiGraphics g, int cx, int cy, int half) {
        int[] rp = getRadarScreenPos(cx, cy, half);

        float scale = (float) half / getWorldRadius();
        int detectPx = (int) (DRONE_DETECT_RADIUS * scale);
        if (detectPx > 8 && detectPx < half * 3) {
            drawCircle(g, rp[0], rp[1], detectPx, 0xC040c070, 48);
            String distLabel = DRONE_DETECT_RADIUS + "м";
            g.drawString(font, distLabel, rp[0] + 3, rp[1] - detectPx - 11, 0xFF60e080, false);
        }

        int halfPx = detectPx / 2;
        if (halfPx > 8) {
            drawCircle(g, rp[0], rp[1], halfPx, 0x9030a058, 32);
            String halfLabel = (DRONE_DETECT_RADIUS / 2) + "м";
            g.drawString(font, halfLabel, rp[0] + 3, rp[1] - halfPx - 11, 0xFF50b068, false);
        }
    }

    private void renderSweep(GuiGraphics g, int cx, int cy, int half) {
        int[] rp = getRadarScreenPos(cx, cy, half);

        float scale = (float) half / getWorldRadius();
        int detectPx = (int) (DRONE_DETECT_RADIUS * scale);
        int sweepLen = Math.min(detectPx, half + 50);

        double rad = Math.toRadians(scanAngle);
        int ex = rp[0] + (int)(Math.cos(rad) * sweepLen);
        int ey = rp[1] - (int)(Math.sin(rad) * sweepLen);
        drawLine(g, rp[0], rp[1], ex, ey, COL_SWEEP);
    }

    private void renderTargets(GuiGraphics g, int cx, int cy, int half, int mx, int my) {
        int wr = getWorldRadius();
        float scale = (float) half / wr;
        int viewCX = radarPos.getX() + (int) panWorldX;
        int viewCZ = radarPos.getZ() + (int) panWorldZ;

        for (RadarTarget target : targets.values()) {
            Vec3 pos = target.smoothPos;
            if (pos == null) continue;

            float relX = (float)(pos.x - viewCX) * scale;
            float relZ = (float)(pos.z - viewCZ) * scale;
            if (Math.abs(relX) > half || Math.abs(relZ) > half) continue;

            int sx = cx + (int) relX;
            int sy = cy + (int) relZ;

            int alpha = target.staleTicks < 40 ? 255 : Math.max(40, 255 - (target.staleTicks - 40) * 5);
            int baseCol = getTargetColor(target.type);
            int symCol = (alpha << 24) | (baseCol & 0xFFFFFF);

            List<Vec3> trail = target.trail;
            int prevTx = sx, prevTy = sy;
            for (int i = trail.size() - 1; i >= 0; i--) {
                float fade = 0.15f + 0.55f * (float)(i) / Math.max(1, trail.size() - 1);
                int ta = (int)(alpha * fade);
                if (ta < 15) continue;
                Vec3 tp = trail.get(i);
                int tx = cx + (int)((tp.x - viewCX) * scale);
                int ty = cy + (int)((tp.z - viewCZ) * scale);
                drawLine(g, prevTx, prevTy, tx, ty, (ta << 24) | (baseCol & 0xFFFFFF));
                prevTx = tx; prevTy = ty;
            }

            drawFilledCircle(g, sx, sy, 2, symCol);

            if (target.entityId == selectedTargetId) {
                drawCircle(g, sx, sy, 4, COL_SELECTED, 16);
            }

            if (Math.abs(mx - sx) < 8 && Math.abs(my - sy) < 8) {
                hoveredTargetId = target.entityId;
            }
        }
    }

    private void renderRadarMarker(GuiGraphics g, int cx, int cy, int half) {
        int[] rp = getRadarScreenPos(cx, cy, half);
        int mx = rp[0], my = rp[1];

        if (Math.abs(mx - cx) <= half && Math.abs(my - cy) <= half) {
            drawFilledCircle(g, mx, my, 4, COL_RADAR_DOT);
            drawFilledCircle(g, mx, my, 2, 0xFF10602a);
            g.hLine(mx - 7, mx - 4, my, COL_RADAR_DOT);
            g.hLine(mx + 4, mx + 7, my, COL_RADAR_DOT);
            g.vLine(mx, my - 7, my - 4, COL_RADAR_DOT);
            g.vLine(mx, my + 4, my + 7, COL_RADAR_DOT);
        }
    }

    private int[] getRadarScreenPos(int cx, int cy, int half) {
        int bpp = ZOOM_LEVELS[zoomIndex];
        int viewCX = radarPos.getX() + (int) panWorldX;
        int viewCZ = radarPos.getZ() + (int) panWorldZ;

        int texOffX = (radarPos.getX() - viewCX) / bpp;
        int texOffZ = (radarPos.getZ() - viewCZ) / bpp;

        float pxToScreen = (float) half / 256.0f;
        return new int[]{
                cx + Math.round(texOffX * pxToScreen),
                cy + Math.round(texOffZ * pxToScreen)
        };
    }

    private void renderCompass(GuiGraphics g, int cx, int cy, int half) {
        g.drawCenteredString(font, "N", cx, cy - half + 2, COL_COMPASS);
        g.drawCenteredString(font, "S", cx, cy + half - 10, COL_COMPASS);
        g.drawString(font, "W", cx - half + 2, cy - 4, COL_COMPASS, false);
        int ew = font.width("E");
        g.drawString(font, "E", cx + half - ew - 2, cy - 4, COL_COMPASS, false);
    }

    private void renderHUD(GuiGraphics g, int cx, int cy, int half) {

        String title = "\u25C9 RADAR";
        g.drawCenteredString(this.font, title, cx, cy - half - 14, COL_TITLE);

        int lx = cx - half;
        int ly = cy - half + 14;
        int wr = getWorldRadius();
        g.drawString(this.font, String.format("RANGE %dm", wr), lx + 3, ly, COL_TEXT_DIM);
        g.drawString(this.font, String.format("SCALE %d bl/px", ZOOM_LEVELS[zoomIndex]),
                lx + 3, ly + 11, COL_TEXT_DIM);

        if (terrainMap != null && terrainMap.isQueued()) {
            g.drawCenteredString(this.font, "\u25CB \u0412 \u041e\u0427\u0415\u0420\u0415\u0414\u0418: #" + terrainMap.getQueuePosition(), cx, cy - half + 20, 0xFFffcc40);
        } else if (terrainMap != null && terrainMap.isBuilding()) {
            int pct = (int)(terrainMap.getBuildProgress() * 100);
            String scan = String.format("\u25CB SCANNING... %d%%", pct);
            g.drawCenteredString(this.font, scan, cx, cy - half + 20, 0xFF60ff90);
            int barW = half;
            int barX = cx - barW / 2;
            int barY = cy - half + 32;
            g.fill(barX, barY, barX + barW, barY + 3, 0xFF1a2a1a);
            g.fill(barX, barY, barX + (int)(barW * terrainMap.getBuildProgress()), barY + 3, 0xFF40ff80);
        }

        int rx = cx + half;
        String coord = String.format("X:%d Z:%d", radarPos.getX(), radarPos.getZ());
        int cw = this.font.width(coord);
        g.drawString(this.font, coord, rx - cw - 3, ly, COL_TEXT_DIM);

        if (panWorldX != 0 || panWorldZ != 0) {
            String view = String.format("VIEW %+d %+d", (int) panWorldX, (int) panWorldZ);
            int vw = this.font.width(view);
            g.drawString(this.font, view, rx - vw - 3, ly + 11, COL_TEXT_DIM);
        }
    }

    private void renderStatusBar(GuiGraphics g, int cx, int cy, int half) {
        int barX = cx - half - 2;
        int barW = half * 2 + 4;
        int barY = cy + half + 4;
        int barH = 14;

        g.fill(barX, barY, barX + barW, barY + barH, COL_STATUS_BG);
        g.hLine(barX, barX + barW - 1, barY, 0xFF1a3028);

        long activeCount = targets.values().stream().filter(t -> t.staleTicks < RadarTarget.STALE_REMOVE).count();
        String countStr = activeCount > 0
                ? "\u26A0 \u0426\u0415\u041b\u0418: " + activeCount
                : "\u2714 \u0427\u0418\u0421\u0422\u041e";
        int countCol = activeCount > 0 ? 0xFFff5050 : 0xFF50ff80;
        g.drawString(font, countStr, barX + 4, barY + 3, countCol, false);

        g.drawString(font, "[Scroll] Zoom  [LMB] Pan  [RMB] Reset  [Click target] Select",
                barX + barW / 3, barY + 3, 0xFF304030, false);
    }

    private List<RadarTarget> getSortedTargets() {
        List<RadarTarget> sorted = new ArrayList<>();
        for (RadarTarget t : targets.values()) {
            if (t.position != null) sorted.add(t);
        }
        sorted.sort((a, b) -> {
            double da = (a.position.x - radarPos.getX()) * (a.position.x - radarPos.getX())
                      + (a.position.z - radarPos.getZ()) * (a.position.z - radarPos.getZ());
            double db = (b.position.x - radarPos.getX()) * (b.position.x - radarPos.getX())
                      + (b.position.z - radarPos.getZ()) * (b.position.z - radarPos.getZ());
            return Double.compare(da, db);
        });
        return sorted;
    }

    private void renderRightPanel(GuiGraphics g, int cx, int cy, int half, int mx, int my) {
        int px = cx + half + 6;
        int py = cy - half;
        int pw = RIGHT_PANEL_W;
        int ph = half * 2;

        g.fill(px, py, px + pw, py + ph, COL_STATUS_BG);
        g.vLine(px, py, py + ph, 0xFF1a3028);

        g.drawString(font, "\u0426\u0415\u041b\u0418", px + 4, py + 3, COL_TITLE, false);
        g.hLine(px + 2, px + pw - 2, py + 13, 0xFF1a3028);

        int listTop = py + 16;
        int bottomReserve = 72; 
        boolean swOn = com.synarsis.airtacticalarsenal.compat.SuperbWarfareCompat.isLoaded();
        boolean vvpOn = com.synarsis.airtacticalarsenal.compat.SuperbWarfareCompat.isVvpLoaded();
        if (swOn || vvpOn) bottomReserve += 26; 
        int listBottom = py + ph - bottomReserve;
        int maxRows = (listBottom - listTop) / 20;

        List<RadarTarget> sorted = getSortedTargets();
        int totalTargets = sorted.size();
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, totalTargets - maxRows));

        g.enableScissor(px, listTop - 1, px + pw, listBottom);

        int row = listTop;
        for (int idx = targetScrollOffset; idx < totalTargets && idx < targetScrollOffset + maxRows; idx++) {
            RadarTarget t = sorted.get(idx);

            boolean selected = t.entityId == selectedTargetId;
            if (selected) g.fill(px + 1, row - 1, px + pw - 1, row + 18, 0x30ffff40);

            boolean hovered = mx >= px && mx <= px + pw && my >= row - 1 && my < row + 18;
            if (hovered && !selected) g.fill(px + 1, row - 1, px + pw - 1, row + 18, 0x18ffffff);

            String typeTag = getTargetTypeTag(t.type);
            String idStr = typeTag.isEmpty()
                    ? String.format("\u25CF #%03d", t.entityId % 1000)
                    : String.format("\u25CF %s #%03d", typeTag, t.entityId % 1000);
            g.drawString(font, idStr, px + 4, row, getTargetColor(t.type), false);

            double dist = Math.sqrt(
                    (t.position.x - radarPos.getX()) * (t.position.x - radarPos.getX()) +
                    (t.position.z - radarPos.getZ()) * (t.position.z - radarPos.getZ()));
            g.drawString(font, String.format("\u0414:%.0f\u043c", dist), px + 60, row, COL_TEXT_DIM, false);

            g.drawString(font, String.format("ALT:%.0f", t.position.y), px + 4, row + 9, 0xFF405840, false);
            g.drawString(font, String.format("V:%.1f", t.speed * 20), px + 60, row + 9, 0xFF405840, false);

            g.hLine(px + 2, px + pw - 2, row + 19, 0xFF0e1a14);
            row += 20;
        }

        g.disableScissor();

        if (totalTargets > maxRows) {
            int barH = Math.max(8, (int)((float) maxRows / totalTargets * (listBottom - listTop)));
            int barY = listTop + (int)((float) targetScrollOffset / totalTargets * (listBottom - listTop));
            g.fill(px + pw - 3, barY, px + pw - 1, barY + barH, 0x40ffffff);
        }

        int bottomY = py + ph - 4;

        bottomY -= 10; g.drawString(font, "[Scroll] Zoom [LMB] Pan", px + 4, bottomY, 0xFF304030, false);
        bottomY -= 10; g.drawString(font, "ALT<90: \u0441\u0442\u0435\u043b\u0441", px + 4, bottomY, 0xFF304030, false);
        bottomY -= 10; g.drawString(font, "V=\u0441\u043a\u043e\u0440. ALT=\u0432\u044b\u0441. \u0414=\u0434\u0438\u0441\u0442.", px + 4, bottomY, 0xFF304030, false);
        bottomY -= 4;
        g.hLine(px + 2, px + pw - 2, bottomY, 0xFF1a3028);
        bottomY -= 2;

        boolean hasSw = com.synarsis.airtacticalarsenal.compat.SuperbWarfareCompat.isLoaded();
        boolean hasVvp = com.synarsis.airtacticalarsenal.compat.SuperbWarfareCompat.isVvpLoaded();
        if (hasSw || hasVvp) {
            if (hasSw) {
                bottomY -= 10; g.drawString(font, "PLN \u0421\u0430\u043c\u043e\u043b\u0451\u0442", px + 4, bottomY, COL_SW_PLANE, false);
            }
            bottomY -= 10; g.drawString(font, "HEL \u0412\u0435\u0440\u0442\u043e\u043b\u0451\u0442", px + 4, bottomY, COL_SW_HELI, false);
            bottomY -= 4;
            g.hLine(px + 2, px + pw - 2, bottomY, 0xFF1a3028);
            bottomY -= 2;
        }

        int valCol = 0xFF80c890;
        bottomY -= 10;
        g.drawString(font, "\u041e\u0440\u043b\u0430\u043d-10", px + 4, bottomY, 0xFF40a0c0, false);
        g.drawString(font, "V:16-24 ALT:100-350", px + 62, bottomY, valCol, false);
        bottomY -= 10;
        g.drawString(font, "\u0418\u0441\u043a\u0430\u043d\u0434\u0435\u0440", px + 4, bottomY, 0xFFc0a040, false);
        g.drawString(font, "V:50-100 ALT:200+", px + 62, bottomY, valCol, false);
        bottomY -= 10;
        g.drawString(font, "\u0428\u0430\u0445\u0435\u0434", px + 4, bottomY, 0xFFc06040, false);
        g.drawString(font, "V:10-30 ALT:80-250", px + 62, bottomY, valCol, false);
        bottomY -= 2;
        g.hLine(px + 2, px + pw - 2, bottomY, 0xFF1a3028);
    }

    private static int getTargetColor(byte type) {
        return switch (type) {
            case 3 -> COL_SW_HELI;
            case 4 -> COL_SW_PLANE;
            case 5 -> COL_SW_DRONE;
            default -> COL_TARGET;
        };
    }

    private static String getTargetTypeTag(byte type) {
        return switch (type) {
            case 3 -> "HEL";
            case 4 -> "PLN";
            default -> "";
        };
    }

    private void drawFilledCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int hw = (int) Math.sqrt(r * r - y * y);
            g.hLine(cx - hw, cx + hw, cy + y, color);
        }
    }

    private void drawCircle(GuiGraphics g, int cx, int cy, int radius, int color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a1 = 2 * Math.PI * i / segments;
            double a2 = 2 * Math.PI * (i + 1) / segments;
            int x1 = cx + (int)(Math.cos(a1) * radius);
            int y1 = cy + (int)(Math.sin(a1) * radius);
            int x2 = cx + (int)(Math.cos(a2) * radius);
            int y2 = cy + (int)(Math.sin(a2) * radius);
            drawLine(g, x1, y1, x2, y2, color);
        }
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        if (dy == 0) { g.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color); return; }
        if (dx == 0) { g.vLine(x1, Math.min(y1, y2), Math.max(y1, y2), color); return; }
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int runStart = x1, curY = y1;
        int steps = 0, maxSteps = dx + dy + 1;
        while (steps++ < maxSteps) {
            if (x1 == x2 && y1 == y2) {
                g.hLine(Math.min(runStart, x1), Math.max(runStart, x1), curY, color);
                break;
            }
            int e2 = 2 * err;
            boolean stepped = false;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 <  dx) {

                g.hLine(Math.min(runStart, x1 - sx), Math.max(runStart, x1 - sx), curY, color);
                err += dx; y1 += sy;
                curY = y1; runStart = x1;
                stepped = true;
            }
            if (!stepped && steps == maxSteps) {
                g.hLine(Math.min(runStart, x1), Math.max(runStart, x1), curY, color);
            }
        }
    }

    private int getWorldRadius() {
        return ZOOM_LEVELS[zoomIndex] * 256;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {

        int panelSpace = RIGHT_PANEL_W + 6;
        int cxMap = (this.width - panelSpace) / 2;
        int mapHalf = (Math.min(this.width - panelSpace, this.height) - 50) / 2;
        int px = cxMap + mapHalf + 6;
        if (mx >= px && mx <= px + RIGHT_PANEL_W) {
            if (delta < 0) targetScrollOffset++;
            else           targetScrollOffset = Math.max(0, targetScrollOffset - 1);
            return true;
        }
        if (delta > 0) zoomIndex = Math.max(zoomIndex - 1, 0);
        else           zoomIndex = Math.min(zoomIndex + 1, ZOOM_LEVELS.length - 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {

            int panelSpace = RIGHT_PANEL_W + 6;
            int cxMap = (this.width - panelSpace) / 2;
            int cyMap = this.height / 2;
            int mapHalf = (Math.min(this.width - panelSpace, this.height) - 50) / 2;
            int px = cxMap + mapHalf + 6;
            int py = cyMap - mapHalf;
            int pw = RIGHT_PANEL_W;
            if (mx >= px && mx <= px + pw) {
                List<RadarTarget> sorted = getSortedTargets();
                int row = py + 16;
                int listBottom = py + (Math.min(this.width - panelSpace, this.height) - 50) - 132;
                for (int idx = targetScrollOffset; idx < sorted.size(); idx++) {
                    if (row >= listBottom) break;
                    RadarTarget t = sorted.get(idx);
                    if (my >= row - 1 && my < row + 18) {
                        selectedTargetId = (selectedTargetId == t.entityId) ? -1 : t.entityId;
                        return true;
                    }
                    row += 20;
                }
            }
            if (hoveredTargetId >= 0) {
                selectedTargetId = (selectedTargetId == hoveredTargetId) ? -1 : hoveredTargetId;
            } else {
                isDragging = true;
            }
            return true;
        }
        if (btn == 1) {
            panWorldX = 0; panWorldZ = 0;
            selectedTargetId = -1;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) { isDragging = false; return true; }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (isDragging && btn == 0) {
            int panelSpace = RIGHT_PANEL_W + 6;
            int mapHalf = (Math.min(this.width - panelSpace, this.height) - 50) / 2;
            int wr = getWorldRadius();
            float scale = (float) mapHalf / wr;

            panWorldX -= (float) dx / scale;
            panWorldZ -= (float) dy / scale;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
