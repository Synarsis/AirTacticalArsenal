package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.ModKeys;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Card;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Layout;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelClearance;
import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules;
import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Denial;
import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Refusal;
import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.entity.tel.TelSequence;
import com.synarsis.airtacticalarsenal.entity.tel.TelState;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelBoneGroups;
import com.synarsis.airtacticalarsenal.item.ControlTabletItem;
import com.synarsis.airtacticalarsenal.item.OrlanTabletItem;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.TelCommandPacket;
import com.synarsis.airtacticalarsenal.network.TelSetTargetPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@OnlyIn(Dist.CLIENT)
public class TelFireControlScreen extends Screen {

    private static final int CONFIRM_TICKS = 60;
    private static final int TOOLTIP_WIDTH = 220;

    private record Check(Component label, boolean known, boolean ok, List<Component> detail) {
    }

    private final IskanderTelEntity tel;

    private Layout layout;
    private final String[] xText = {"", ""};
    private final String[] zText = {"", ""};
    private boolean prefilled;
    private final EditBox[] xs = new EditBox[2];
    private final EditBox[] zs = new EditBox[2];
    private final TelButton[] ok = new TelButton[2];
    private final TelButton[] load = new TelButton[2];
    private final TelButton[] unload = new TelButton[2];
    private final TelButton[] prepare = new TelButton[2];
    private TelButton copyButton;
    private TelButton tabletButton;
    private TelButton minusButton;
    private TelButton plusButton;
    private TelButton bothButton;
    private final TelButton[] bottom = new TelButton[5];
    private final List<TelButton> buttons = new ArrayList<>();

    private int focusedCard;
    private int tabletIndex = -1;
    private int confirmTicks;
    private int scroll;
    private List<Check> checks = List.of();
    @Nullable
    private TelState lastState;

    public TelFireControlScreen(IskanderTelEntity tel) {
        super(Component.translatable("gui.ata.tel.title"));
        this.tel = tel;
    }

    @Override
    protected void init() {
        this.layout = TelPanelLayout.compute(this.width, this.height);
        this.buttons.clear();
        for (int i = 0; i < 2; i++) {
            if (this.xs[i] != null) {
                this.xText[i] = this.xs[i].getValue();
                this.zText[i] = this.zs[i].getValue();
            } else if (!this.prefilled && this.tel.hasTarget(i)) {
                this.xText[i] = Integer.toString(this.tel.getTargetX(i));
                this.zText[i] = Integer.toString(this.tel.getTargetZ(i));
            }
        }
        this.prefilled = true;

        for (int i = 0; i < 2; i++) {
            final int slot = i;
            Card c = this.layout.cards()[i];
            this.xs[i] = this.field(c.xField(), "X", this.xText[i], v -> this.xText[slot] = v);
            this.zs[i] = this.field(c.zField(), "Z", this.zText[i], v -> this.zText[slot] = v);
            this.ok[i] = this.button(c.ok(), () -> Component.literal("✓"), false, () -> this.sendTarget(slot));
            this.load[i] = this.button(c.load(), () -> Component.translatable("gui.ata.tel.load"), false,
                    () -> this.command(IskanderTelEntity.CMD_LOAD, slot, 0));
            this.unload[i] = this.button(c.unload(), () -> Component.translatable("gui.ata.tel.unload"), false,
                    () -> this.command(IskanderTelEntity.CMD_UNLOAD, slot, 0));
            this.prepare[i] = this.button(c.prepare(),
                    () -> Component.translatable(this.stands(slot) ? "gui.ata.tel.lower" : "gui.ata.tel.prepare"), false,
                    () -> this.command(this.stands(slot) ? IskanderTelEntity.CMD_LOWER : IskanderTelEntity.CMD_PREPARE,
                            1 << slot, 0));
        }
        this.copyButton = this.button(this.layout.copy(), () -> Component.translatable("gui.ata.tel.copy"), false,
                this::copyTarget);
        this.tabletButton = this.button(this.layout.tablet(), () -> Component.translatable("gui.ata.tel.from_tablet"),
                false, this::cycleTablet);
        this.minusButton = this.button(this.layout.minus(), () -> Component.literal("-"), false,
                () -> this.command(IskanderTelEntity.CMD_SET_INTERVAL, 0,
                        this.tel.getSalvoInterval() - (hasShiftDown() ? 5 : 1)));
        this.plusButton = this.button(this.layout.plus(), () -> Component.literal("+"), false,
                () -> this.command(IskanderTelEntity.CMD_SET_INTERVAL, 0,
                        this.tel.getSalvoInterval() + (hasShiftDown() ? 5 : 1)));
        this.bothButton = this.button(this.layout.both(), () -> Component.translatable("gui.ata.tel.prepare_both"),
                false, () -> this.command(IskanderTelEntity.CMD_PREPARE, 3, 0));

        Rect[] row = this.layout.bottom();
        this.bottom[0] = this.button(row[0], this::coversLabel, false,
                () -> this.command(IskanderTelEntity.CMD_COVERS, 0, 0));
        this.bottom[1] = this.button(row[1], () -> Component.translatable("gui.ata.tel.lower"), false,
                () -> this.command(IskanderTelEntity.CMD_LOWER, this.tel.getRaisedMask(), 0));
        this.bottom[2] = this.button(row[2], () -> Component.translatable("gui.ata.tel.stow"), false,
                () -> this.command(IskanderTelEntity.CMD_STOW, 0, 0));
        this.bottom[3] = this.button(row[3], () -> Component.translatable("gui.ata.tel.abort"), false,
                () -> this.command(IskanderTelEntity.CMD_ABORT, 0, 0));
        this.bottom[4] = this.button(row[4], () -> Component.translatable(
                this.confirmTicks > 0 ? "gui.ata.tel.confirm" : "gui.ata.tel.launch"), true, this::pressLaunch);

        this.scroll = Mth.clamp(this.scroll, 0, this.layout.maxScroll());
        this.refresh();
    }

    private EditBox field(Rect r, String name, String value, java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, r.x(), r.y(), r.w(), r.h(), Component.literal(name));
        box.setMaxLength(10);
        box.setFilter(TelFireControlScreen::isValidNumber);
        box.setHint(Component.literal(name).withStyle(s -> s.withColor(TelUi.DIM)));
        box.setValue(value);
        box.setResponder(onChange);
        this.addRenderableWidget(box);
        return box;
    }

    private TelButton button(Rect r, java.util.function.Supplier<Component> label, boolean danger, Runnable action) {
        TelButton b = new TelButton(r, label, danger, action);
        this.buttons.add(b);
        this.addRenderableWidget(b);
        return b;
    }

    private static boolean isValidNumber(String input) {
        if (input.isEmpty() || input.equals("-")) {
            return true;
        }
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNumber(String s) {
        return !s.isEmpty() && !s.equals("-") && isValidNumber(s);
    }

    private void command(byte command, int mask, int arg) {
        NetworkHandler.sendToServer(new TelCommandPacket(this.tel.getId(), command, (byte) mask, arg));
    }

    private void sendTarget(int slot) {
        if (isNumber(this.xs[slot].getValue()) && isNumber(this.zs[slot].getValue())) {
            NetworkHandler.sendToServer(new TelSetTargetPacket(this.tel.getId(), (byte) slot,
                    Integer.parseInt(this.xs[slot].getValue()), Integer.parseInt(this.zs[slot].getValue())));
        }
    }

    private void copyTarget() {
        this.xs[1].setValue(this.xs[0].getValue());
        this.zs[1].setValue(this.zs[0].getValue());
        this.sendTarget(1);
    }

    private void pressLaunch() {
        if (this.confirmTicks > 0) {
            this.confirmTicks = 0;
            this.command(IskanderTelEntity.CMD_LAUNCH, 0, 0);
        } else {
            this.confirmTicks = CONFIRM_TICKS;
        }
    }

    private void cycleTablet() {
        Map<Integer, int[]> saved = this.tabletCoords();
        if (saved.isEmpty()) {
            return;
        }
        List<Integer> slots = new ArrayList<>(saved.keySet());
        this.tabletIndex = (this.tabletIndex + 1) % slots.size();
        int[] xz = saved.get(slots.get(this.tabletIndex));
        int card = this.focusedCard;
        this.xs[card].setValue(Integer.toString(xz[0]));
        this.zs[card].setValue(Integer.toString(xz[1]));
        this.sendTarget(card);
    }

    private Map<Integer, int[]> tabletCoords() {
        Map<Integer, int[]> none = new TreeMap<>();
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return none;
        }
        List<ItemStack> candidates = new ArrayList<>();
        candidates.add(player.getMainHandItem());
        candidates.add(player.getOffhandItem());
        candidates.addAll(player.getInventory().items);
        for (ItemStack stack : candidates) {
            Map<Integer, int[]> saved = null;
            if (stack.getItem() instanceof ControlTabletItem) {
                saved = ControlTabletItem.getSavedCoords(stack);
            } else if (stack.getItem() instanceof OrlanTabletItem) {
                saved = OrlanTabletItem.getSavedCoords(stack);
            }
            if (saved != null && !saved.isEmpty()) {
                return new TreeMap<>(saved);
            }
        }
        return none;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null
                || this.tel.isRemoved() || this.minecraft.player.getVehicle() != this.tel
                || !this.tel.isCrewSeat(this.minecraft.player)) {
            this.onClose();
            return;
        }
        if (this.confirmTicks > 0) {
            this.confirmTicks--;
        }
        TelState state = this.tel.getTelState();
        if (state != this.lastState) {
            this.lastState = state;
            this.confirmTicks = 0;
        }
        for (int i = 0; i < 2; i++) {
            if (this.xs[i].isFocused() || this.zs[i].isFocused()) {
                this.focusedCard = i;
            }
        }
        this.refresh();
    }

    private Component coversLabel() {
        TelState s = this.tel.getTelState();
        return Component.translatable(s == TelState.COVERS_OPEN ? "gui.ata.tel.covers_close"
                : (s == TelState.STOWED ? "gui.ata.tel.covers_open" : "gui.ata.tel.covers"));
    }

    private static Component reasonOf(Refusal r) {
        return TelUi.reason(Denial.of(r));
    }

    private void refresh() {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return;
        }
        TelSequence view = this.tel.seqView();
        for (int i = 0; i < 2; i++) {
            this.set(this.load[i], this.tel.loadDenial(player, i));
            this.set(this.unload[i], this.tel.unloadDenial(i));
            this.set(this.prepare[i], this.stands(i) ? this.tel.lowerDenial(1 << i) : this.tel.prepareDenial(1 << i));
            boolean typed = isNumber(this.xs[i].getValue()) && isNumber(this.zs[i].getValue());
            this.ok[i].setState(typed, Component.translatable("gui.ata.tel.why.enter_target"));
        }
        this.set(this.bothButton, this.tel.prepareDenial(3));
        this.set(this.bottom[0], this.tel.coversDenial());
        this.set(this.bottom[1], this.tel.lowerDenial(this.tel.getRaisedMask()));
        this.set(this.bottom[2], Denial.of(view.canStow()));
        boolean counting = this.tel.getTelState() == TelState.COUNTDOWN;
        this.bottom[3].setState(counting, Component.translatable("gui.ata.tel.why.no_countdown"));
        Denial launch = this.tel.launchDenial();
        this.bottom[4].setState(launch.ok(), TelUi.reason(launch));
        this.minusButton.setState(this.tel.getSalvoInterval() > TelSequence.MIN_INTERVAL_SECONDS, null);
        this.plusButton.setState(this.tel.getSalvoInterval() < TelSequence.MAX_INTERVAL_SECONDS, null);
        this.copyButton.setState(isNumber(this.xs[0].getValue()) && isNumber(this.zs[0].getValue()),
                Component.translatable("gui.ata.tel.why.enter_target"));
        this.tabletButton.setState(!this.tabletCoords().isEmpty(), Component.translatable("gui.ata.tel.why.no_tablet"));
        this.buildChecks();
    }

    private void set(TelButton button, Denial denial) {
        button.setState(denial.ok(), TelUi.reason(denial));
    }

    @Nullable
    private TelLaunchRules.Aim safeAim(int slot) {
        try {
            return this.tel.aim(slot);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private void buildChecks() {
        List<Check> out = new ArrayList<>();
        TelLaunchRules.Stance st = this.tel.stance();
        double maxTilt;
        try {
            maxTilt = ShahedConfig.getTelMaxLaunchTilt();
        } catch (IllegalStateException e) {
            maxTilt = 8.0;
        }
        out.add(new Check(Component.translatable("gui.ata.tel.check.still"), true,
                st.speed() < TelLaunchRules.STANDSTILL_SPEED,
                List.of(Component.translatable("gui.ata.tel.detail.speed", TelUi.number(st.speed() * 72.0, 1)))));
        out.add(new Check(Component.translatable("gui.ata.tel.check.ground"), true, st.onGround(), List.of()));
        out.add(new Check(Component.translatable("gui.ata.tel.check.water"), true, !st.inWater(), List.of()));
        boolean level = Math.abs(st.pitchDeg()) <= maxTilt && Math.abs(st.rollDeg()) <= maxTilt;
        out.add(new Check(Component.translatable("gui.ata.tel.check.level"), true, level,
                List.of(Component.translatable("gui.ata.tel.detail.tilt", TelUi.number(st.pitchDeg(), 1),
                        TelUi.number(st.rollDeg(), 1), TelUi.number(maxTilt, 0)))));

        IskanderTelEntity.Clearance room = this.tel.clearance();
        boolean shut = this.tel.getTelState() == TelState.STOWED;
        out.add(new Check(Component.translatable("gui.ata.tel.check.roofs"), shut, room.roof() == null,
                detailBlock(null, room.roof())));

        List<Component> erectorDetail = new ArrayList<>();
        List<Component> skyDetail = new ArrayList<>();
        boolean erectorKnown = false;
        boolean erectorOk = true;
        boolean skyKnown = false;
        boolean skyOk = true;
        for (int slot = 0; slot < 2; slot++) {
            boolean aboard = this.tel.isLoaded(slot);
            boolean up = this.tel.isRaisedNow(slot);
            if (aboard && !up) {
                erectorKnown = true;
                if (room.erector()[slot] != null) {
                    erectorOk = false;
                    erectorDetail.addAll(detailBlock(slot, room.erector()[slot]));
                }
            }
            if (aboard || up) {
                skyKnown = true;
                if (room.sky()[slot] != null) {
                    skyOk = false;
                    skyDetail.addAll(detailBlock(slot, room.sky()[slot]));
                }
            }
        }
        out.add(new Check(Component.translatable("gui.ata.tel.check.erector"), erectorKnown, erectorOk, erectorDetail));
        out.add(new Check(Component.translatable("gui.ata.tel.check.sky"), skyKnown, skyOk, skyDetail));

        for (int slot = 0; slot < 2; slot++) {
            TelLaunchRules.Aim aim = this.safeAim(slot);
            boolean aboard = this.tel.isLoaded(slot);
            if (aim == null || !aboard) {
                out.add(new Check(Component.translatable("gui.ata.tel.check.target", slot + 1), false, false, List.of()));
                continue;
            }
            Denial d = TelLaunchRules.aim(aim, slot);
            List<Component> detail = new ArrayList<>();
            if (!d.ok()) {
                detail.add(TelUi.reason(d));
            } else {
                detail.add(Component.translatable("gui.ata.tel.detail.distance", Math.round(aim.distance())));
            }
            out.add(new Check(Component.translatable("gui.ata.tel.check.target", slot + 1), true, d.ok(), detail));
        }
        this.checks = out;
    }

    private static List<Component> detailBlock(@Nullable Integer slot, @Nullable BlockPos at) {
        if (at == null) {
            return List.of();
        }
        Component where = Component.translatable("gui.ata.tel.detail.block", TelClearance.describe(at));
        return slot == null ? List.of(where)
                : List.of(Component.translatable("gui.ata.tel.detail.round", slot + 1).append(" ").append(where));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, 0xA0080808, 0xB0080808);
        Layout l = this.layout;
        g.fill(l.panel().x(), l.panel().y(), l.panel().right(), l.panel().bottom(), TelUi.PANEL);
        TelUi.frame(g, l.panel(), TelUi.BORDER);
        g.fill(l.header().x() - 4, l.header().y() - 4, l.header().right() + 4, l.header().bottom() + 1, TelUi.STRIP);

        this.drawHeader(g, partialTick);
        for (int i = 0; i < 2; i++) {
            this.drawCard(g, i);
        }

        if (!l.compact()) {
            Rect label = TelPanelLayout.checksLabel(l);
            TelUi.text(g, this.font, Component.translatable("gui.ata.tel.checks"), label, TelUi.SECTION);
        }
        TelUi.text(g, this.font, Component.translatable("gui.ata.tel.salvo"), l.salvoLabel(), TelUi.SECTION);
        TelUi.textCentred(g, this.font, Component.translatable("gui.ata.tel.seconds", this.tel.getSalvoInterval()),
                l.value(), TelUi.TEXT);

        this.drawChecks(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
        this.drawTooltips(g, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics g, float partialTick) {
        Layout l = this.layout;
        TelUi.text(g, this.font, this.title, l.title(), TelUi.BORDER);

        TelState state = this.tel.getTelState();
        Component chip = state == TelState.COUNTDOWN
                ? Component.translatable("gui.ata.tel.chip.countdown", TelUi.seconds(this.nextLaunchTicks()))
                : TelUi.stateName(state);
        int colour = TelUi.stateColour(state);
        g.fill(l.chip().x(), l.chip().y() + 2, l.chip().right(), l.chip().bottom() - 1, 0xFF0E0F10);
        TelUi.frame(g, new Rect(l.chip().x(), l.chip().y() + 2, l.chip().w(), l.chip().h() - 3), colour);
        TelUi.textCentred(g, this.font, chip, l.chip(), colour);

        g.fill(l.progress().x(), l.progress().y(), l.progress().right(), l.progress().bottom(), 0xFF26282B);
        float fraction = this.progress(partialTick);
        if (fraction > 0.0f) {
            g.fill(l.progress().x(), l.progress().y(),
                    l.progress().x() + Math.round(l.progress().w() * fraction), l.progress().bottom(), colour);
        }
    }

    private int nextLaunchTicks() {
        int best = Integer.MAX_VALUE;
        for (int slot = 0; slot < 2; slot++) {
            int left = this.tel.getCountdownLeft(slot);
            if (left >= 0) {
                best = Math.min(best, left);
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private float progress(float partialTick) {
        TelState state = this.tel.getTelState();
        if (state.isTransition()) {
            return Mth.clamp((this.tel.getVisualTicks() + partialTick) / this.tel.stateDuration(state), 0.0f, 1.0f);
        }
        if (state == TelState.COUNTDOWN) {
            int left = this.nextLaunchTicks();
            return Mth.clamp(1.0f - (float) left / IskanderTelEntity.PREPARING_TICKS, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    private boolean stands(int slot) {
        return this.tel.isRaisedNow(slot) && this.tel.getTelState() == TelState.RAISED;
    }

    private TelPose.Clip lift(int slot) {
        return this.tel.getGroupClip(TelBoneGroups.liftOf(slot));
    }

    private void drawCard(GuiGraphics g, int slot) {
        Card c = this.layout.cards()[slot];
        g.fill(c.box().x(), c.box().y(), c.box().right(), c.box().bottom(), TelUi.CARD);
        TelUi.frame(g, c.box(), TelUi.CARD_BORDER);

        TelUi.text(g, this.font, Component.translatable("gui.ata.tel.round", slot + 1), c.title(), TelUi.SECTION);
        this.drawStatus(g, slot, c.status());

        TelLaunchRules.Aim aim = this.safeAim(slot);
        Component info;
        int infoColour = TelUi.DIM;
        Component error = null;
        if (!this.tel.hasTarget(slot) || aim == null) {
            info = Component.translatable("gui.ata.tel.no_target");
        } else {
            double speed = ShahedConfig.getIskanderCruiseSpeed() * 20.0;
            info = Component.translatable("gui.ata.tel.card.info", Math.round(aim.distance()),
                    Math.round(aim.distance() / Math.max(speed, 0.1)));
            infoColour = TelUi.TEXT;
            Refusal r = TelLaunchRules.aim(aim);
            if (!r.ok()) {
                error = Component.translatable("gui.ata.tel.error." + r.name().toLowerCase(Locale.ROOT));
            }
        }
        if (c.error().isEmpty()) {
            TelUi.text(g, this.font, error != null ? error : info, c.info(), error != null ? TelUi.RED : infoColour);
        } else {
            TelUi.text(g, this.font, info, c.info(), infoColour);
            if (error != null) {
                TelUi.text(g, this.font, error, c.error(), TelUi.RED);
            }
        }
    }

    private void drawStatus(GuiGraphics g, int slot, Rect box) {
        TelState state = this.tel.getTelState();
        boolean loaded = this.tel.isLoaded(slot);
        int left = this.tel.getCountdownLeft(slot);
        Component text;
        int colour;
        if (state == TelState.COUNTDOWN && left >= 0) {
            text = Component.translatable("gui.ata.tel.status.countdown", TelUi.seconds(left));
            colour = left < 40 ? TelUi.RED : TelUi.YELLOW;
        } else if (this.lift(slot) == TelPose.Clip.OPENING) {
            text = Component.translatable("gui.ata.tel.status.rising");
            colour = TelUi.YELLOW;
        } else if (this.lift(slot) == TelPose.Clip.CLOSING) {
            text = Component.translatable("gui.ata.tel.status.lowering");
            colour = TelUi.YELLOW;
        } else if (this.lift(slot) == TelPose.Clip.HELD) {
            text = Component.translatable(loaded ? "gui.ata.tel.status.raised" : "gui.ata.tel.status.gone");
            colour = loaded ? TelUi.BLUE : TelUi.DIM;
        } else if (loaded) {
            text = Component.translatable("gui.ata.tel.status.loaded");
            colour = TelUi.GREEN;
        } else {
            text = Component.translatable("gui.ata.tel.status.empty");
            colour = TelUi.DIM;
        }
        if (this.layout.compact()) {
            TelUi.text(g, this.font, text, box, colour);
        } else {
            TelUi.textRight(g, this.font, text, box, colour);
        }
    }

    private void drawChecks(GuiGraphics g, int mouseX, int mouseY) {
        Layout l = this.layout;
        Rect view = l.checksView();
        if (view.isEmpty()) {
            return;
        }
        g.enableScissor(view.x(), view.y(), view.right(), view.bottom());
        for (int i = 0; i < this.checks.size() && i < TelPanelLayout.CHECK_COUNT; i++) {
            Rect cell = l.checkCell(i, this.scroll);
            if (cell.bottom() < view.y() || cell.y() > view.bottom()) {
                continue;
            }
            Check check = this.checks.get(i);
            String mark = !check.known() ? "-" : (check.ok() ? "✔" : "✖");
            int colour = !check.known() ? TelUi.DIM : (check.ok() ? TelUi.GREEN : TelUi.RED);
            g.drawString(this.font, mark, cell.x(), cell.y() + (cell.h() - 8) / 2, colour, false);
            TelUi.text(g, this.font, check.label(), new Rect(cell.x() + 11, cell.y(), cell.w() - 11, cell.h()),
                    check.known() ? TelUi.TEXT : TelUi.DIM);
        }
        g.disableScissor();
        if (l.maxScroll() > 0) {
            int barH = Math.max(6, view.h() * view.h() / l.checksContentHeight());
            int barY = view.y() + (view.h() - barH) * this.scroll / l.maxScroll();
            g.fill(view.right() - 2, barY, view.right(), barY + barH, TelUi.DIM);
        }
    }

    private void drawTooltips(GuiGraphics g, int mouseX, int mouseY) {
        List<Component> lines = null;
        for (TelButton b : this.buttons) {
            if (!b.contains(mouseX, mouseY)) {
                continue;
            }
            Component label = b.currentLabel();
            boolean cut = this.font.width(label.getString()) > b.getWidth() - 4;
            if (b.why() != null) {
                lines = new ArrayList<>();
                if (cut) {
                    lines.add(label);
                }
                lines.addAll(TelUi.wrap(this.font, b.why().copy().withStyle(s -> s.withColor(TelUi.YELLOW)),
                        TOOLTIP_WIDTH));
            } else if (cut) {
                lines = List.of(label);
            }
            break;
        }
        if (lines == null) {
            Rect view = this.layout.checksView();
            for (int i = 0; i < this.checks.size() && i < TelPanelLayout.CHECK_COUNT; i++) {
                Rect cell = this.layout.checkCell(i, this.scroll);
                if (view.contains(mouseX, mouseY) && cell.contains(mouseX, mouseY)) {
                    Check check = this.checks.get(i);
                    lines = new ArrayList<>();
                    lines.add(check.label());
                    for (Component detail : check.detail()) {
                        lines.addAll(TelUi.wrap(this.font, detail, TOOLTIP_WIDTH));
                    }
                    break;
                }
            }
        }
        if (lines != null && !lines.isEmpty()) {
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.layout.maxScroll() > 0 && this.layout.checksView().contains(mouseX, mouseY)) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY) * this.layout.checkRowHeight(), 0,
                    this.layout.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = 0; i < 2; i++) {
            if ((this.xs[i].isFocused() || this.zs[i].isFocused())
                    && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                this.sendTarget(i);
                return true;
            }
        }
        boolean typing = false;
        for (int i = 0; i < 2; i++) {
            typing |= this.xs[i].isFocused() || this.zs[i].isFocused();
        }
        if (!typing && ModKeys.TEL_FIRE_CONTROL.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
