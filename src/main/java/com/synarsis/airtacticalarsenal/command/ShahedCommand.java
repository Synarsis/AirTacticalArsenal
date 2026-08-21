package com.synarsis.airtacticalarsenal.command;

import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.worldmap.WorldMapData;
import com.synarsis.airtacticalarsenal.worldmap.WorldMapScanner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;
import com.synarsis.airtacticalarsenal.combat.hitbox.HitScan;
import com.synarsis.airtacticalarsenal.combat.outcome.FuzeState;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.synarsis.airtacticalarsenal.network.HitboxDebugPacket;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public class ShahedCommand {

    private static Component onOff(boolean value) {
        return value ? Component.translatable("command.ata.config.on").withStyle(s -> s.withColor(0x55FF55))
                : Component.translatable("command.ata.config.off").withStyle(s -> s.withColor(0xFF5555));
    }

    /** Registers the command tree under {@code /ata}, with {@code /shahed} as an alias. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("ata")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("attack")
                        .then(Commands.literal("shahed")
                                .then(Commands.argument("start", Vec3Argument.vec3())
                                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                                .then(Commands.literal("low")
                                                        .executes(context -> {
                                                            Vec3 startPos = Vec3Argument.getVec3(context, "start");
                                                            BlockPos targetPos = BlockPosArgument.getBlockPos(context,
                                                                    "target");
                                                            ServerLevel level = context.getSource().getLevel();
                                                            ShahedEntity shahed = new ShahedEntity(level, startPos,
                                                                    targetPos, "low");
                                                            shahed.isLegitSpawn = true;
                                                            level.addFreshEntity(shahed);
                                                            context.getSource()
                                                                    .sendSuccess(
                                                                            () -> Component
                                                                                    .translatable(
                                                                                            "command.ata.shahed.low")
                                                                                    .withStyle(
                                                                                            s -> s.withColor(0x55FF55)),
                                                                            true);
                                                            return 1;
                                                        }))
                                                .then(Commands.literal("high")
                                                        .executes(context -> {
                                                            Vec3 startPos = Vec3Argument.getVec3(context, "start");
                                                            BlockPos targetPos = BlockPosArgument.getBlockPos(context,
                                                                    "target");
                                                            ServerLevel level = context.getSource().getLevel();
                                                            ShahedEntity shahed = new ShahedEntity(level, startPos,
                                                                    targetPos, "high");
                                                            shahed.isLegitSpawn = true;
                                                            level.addFreshEntity(shahed);
                                                            context.getSource()
                                                                    .sendSuccess(
                                                                            () -> Component
                                                                                    .translatable(
                                                                                            "command.ata.shahed.high")
                                                                                    .withStyle(
                                                                                            s -> s.withColor(0x55FF55)),
                                                                            true);
                                                            return 1;
                                                        })))))
                        .then(Commands.literal("iskander")
                                .then(Commands.argument("start", Vec3Argument.vec3())
                                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                                .executes(context -> {
                                                    Vec3 startPos = Vec3Argument.getVec3(context, "start");
                                                    BlockPos targetPos = BlockPosArgument.getBlockPos(context,
                                                            "target");
                                                    ServerLevel level = context.getSource().getLevel();
                                                    IskanderEntity iskander = new IskanderEntity(level, startPos,
                                                            targetPos);
                                                    iskander.isLegitSpawn = true;
                                                    level.addFreshEntity(iskander);
                                                    context.getSource().sendSuccess(() -> Component.literal(
                                                            "§a[Искандер-М] Баллистическая ракета запущена! Цель: "
                                                                    + targetPos.getX() + ", " + targetPos.getZ()),
                                                            true);
                                                    return 1;
                                                })))))
                .then(Commands.literal("config")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable("command.ata.config.title")
                                    .withStyle(s -> s.withColor(0xFFFF55)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.spawn_x",
                                                    Component.literal(String.valueOf(ShahedConfig.SPAWN_X.get()))
                                                            .withStyle(s -> s.withColor(0x55FFFF)))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.spawn_y",
                                                    Component.literal(String.valueOf(ShahedConfig.SPAWN_Y.get()))
                                                            .withStyle(s -> s.withColor(0x55FFFF)))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.spawn_z",
                                                    Component.literal(String.valueOf(ShahedConfig.SPAWN_Z.get()))
                                                            .withStyle(s -> s.withColor(0x55FFFF)))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.target_y",
                                                    Component.literal(String.valueOf(ShahedConfig.TARGET_Y_LEVEL.get()))
                                                            .withStyle(s -> s.withColor(0x55FFFF)))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.particles",
                                                    onOff(ShahedConfig.areExplosionParticlesEnabled()))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(
                                            () -> Component
                                                    .translatable("command.ata.config.particle_mult",
                                                            Component
                                                                    .literal(String.valueOf(
                                                                            ShahedConfig.getParticleMultiplier()))
                                                                    .withStyle(s -> s.withColor(0x55FFFF)))
                                                    .withStyle(s -> s.withColor(0xAAAAAA)),
                                            false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.block_damage",
                                                    onOff(ShahedConfig.doExplosionsBreakBlocks()))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.sounds",
                                                    onOff(ShahedConfig.areCustomExplosionSoundsEnabled()))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.blacklist",
                                                    onOff(ShahedConfig.isBlacklistEnabled()))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.whitelist",
                                                    onOff(ShahedConfig.isWhitelistEnabled()))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            context.getSource()
                                    .sendSuccess(() -> Component
                                            .translatable("command.ata.config.logging",
                                                    onOff(ShahedConfig.isLoggingEnabled()))
                                            .withStyle(s -> s.withColor(0xAAAAAA)), false);
                            return 1;
                        })
                        .then(Commands.literal("spawnX")
                                .then(Commands.argument("value", IntegerArgumentType.integer(-30000000, 30000000))
                                        .executes(context -> {
                                            int value = IntegerArgumentType.getInteger(context, "value");
                                            ShahedConfig.SPAWN_X.set(value);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.spawn_x.set", value)
                                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("spawnY")
                                .then(Commands.argument("value", IntegerArgumentType.integer(100, 320))
                                        .executes(context -> {
                                            int value = IntegerArgumentType.getInteger(context, "value");
                                            ShahedConfig.SPAWN_Y.set(value);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.spawn_y.set", value)
                                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("spawnZ")
                                .then(Commands.argument("value", IntegerArgumentType.integer(-30000000, 30000000))
                                        .executes(context -> {
                                            int value = IntegerArgumentType.getInteger(context, "value");
                                            ShahedConfig.SPAWN_Z.set(value);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.spawn_z.set", value)
                                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("targetY")
                                .then(Commands.argument("value", IntegerArgumentType.integer(-64, 320))
                                        .executes(context -> {
                                            int value = IntegerArgumentType.getInteger(context, "value");
                                            ShahedConfig.TARGET_Y_LEVEL.set(value);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.target_y.set", value)
                                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("particles")
                                .executes(context -> {
                                    context.getSource()
                                            .sendSuccess(() -> Component
                                                    .translatable("command.ata.config.particles",
                                                            onOff(ShahedConfig.areExplosionParticlesEnabled()))
                                                    .withStyle(s -> s.withColor(0xFFFF55)), false);
                                    context.getSource()
                                            .sendSuccess(() -> Component
                                                    .translatable("command.ata.config.particle_mult",
                                                            ShahedConfig.getParticleMultiplier())
                                                    .withStyle(s -> s.withColor(0xFFFF55)), false);
                                    return 1;
                                })
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ShahedConfig.setExplosionParticlesEnabled(true);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.particles.enabled")
                                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ShahedConfig.setExplosionParticlesEnabled(false);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.particles.disabled")
                                                            .withStyle(s -> s.withColor(0xFF5555)), true);
                                            return 1;
                                        }))
                                .then(Commands.literal("multiplier")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 500))
                                                .executes(context -> {
                                                    int value = IntegerArgumentType.getInteger(context, "value");
                                                    ShahedConfig.setParticleMultiplier(value);
                                                    context.getSource()
                                                            .sendSuccess(() -> Component.translatable(
                                                                    "command.ata.config.particle_mult.set", value)
                                                                    .withStyle(s -> s.withColor(0x55FF55)), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("blockDamage")
                                .executes(context -> {
                                    context.getSource()
                                            .sendSuccess(() -> Component
                                                    .translatable("command.ata.config.block_damage",
                                                            onOff(ShahedConfig.doExplosionsBreakBlocks()))
                                                    .withStyle(s -> s.withColor(0xFFFF55)), false);
                                    return 1;
                                })
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ShahedConfig.setExplosionsBreakBlocks(true);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.block_damage.enabled")
                                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ShahedConfig.setExplosionsBreakBlocks(false);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.block_damage.disabled")
                                                            .withStyle(s -> s.withColor(0xFF5555)), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("sounds")
                                .executes(context -> {
                                    context.getSource()
                                            .sendSuccess(
                                                    () -> Component
                                                            .translatable("command.ata.config.sounds",
                                                                    onOff(ShahedConfig
                                                                            .areCustomExplosionSoundsEnabled()))
                                                            .withStyle(s -> s.withColor(0xFFFF55)),
                                                    false);
                                    return 1;
                                })
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ShahedConfig.setCustomExplosionSoundsEnabled(true);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.sounds.enabled")
                                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ShahedConfig.setCustomExplosionSoundsEnabled(false);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.sounds.disabled")
                                                            .withStyle(s -> s.withColor(0xFF5555)), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("logging")
                                .executes(context -> {
                                    context.getSource()
                                            .sendSuccess(() -> Component
                                                    .translatable("command.ata.config.logging",
                                                            onOff(ShahedConfig.isLoggingEnabled()))
                                                    .withStyle(s -> s.withColor(0xFFFF55)), false);
                                    return 1;
                                })
                                .then(Commands.literal("enable")
                                        .executes(context -> {
                                            ShahedConfig.setLoggingEnabled(true);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.logging.enabled")
                                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                            return 1;
                                        }))
                                .then(Commands.literal("disable")
                                        .executes(context -> {
                                            ShahedConfig.setLoggingEnabled(false);
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.logging.disabled")
                                                            .withStyle(s -> s.withColor(0xFF5555)), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("zone")
                                .executes(context -> {
                                    context.getSource()
                                            .sendSuccess(() -> Component
                                                    .translatable("command.ata.config.blacklist",
                                                            onOff(ShahedConfig.isBlacklistEnabled()))
                                                    .withStyle(s -> s.withColor(0xFFFF55)), false);
                                    context.getSource()
                                            .sendSuccess(() -> Component
                                                    .translatable("command.ata.config.whitelist",
                                                            onOff(ShahedConfig.isWhitelistEnabled()))
                                                    .withStyle(s -> s.withColor(0xFFFF55)), false);
                                    return 1;
                                })
                                .then(Commands.literal("blacklist")
                                        .executes(context -> {
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.blacklist",
                                                                    onOff(ShahedConfig.isBlacklistEnabled()))
                                                            .withStyle(s -> s.withColor(0xFFFF55)), false);
                                            return 1;
                                        })
                                        .then(Commands.literal("enable")
                                                .executes(context -> {
                                                    ShahedConfig.setBlacklistEnabled(true);
                                                    context.getSource()
                                                            .sendSuccess(() -> Component
                                                                    .translatable(
                                                                            "command.ata.config.blacklist.enabled")
                                                                    .withStyle(s -> s.withColor(0x55FF55)), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("disable")
                                                .executes(context -> {
                                                    ShahedConfig.setBlacklistEnabled(false);
                                                    context.getSource()
                                                            .sendSuccess(() -> Component
                                                                    .translatable(
                                                                            "command.ata.config.blacklist.disabled")
                                                                    .withStyle(s -> s.withColor(0xFF5555)), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("whitelist")
                                        .executes(context -> {
                                            context.getSource()
                                                    .sendSuccess(() -> Component
                                                            .translatable("command.ata.config.whitelist",
                                                                    onOff(ShahedConfig.isWhitelistEnabled()))
                                                            .withStyle(s -> s.withColor(0xFFFF55)), false);
                                            return 1;
                                        })
                                        .then(Commands.literal("enable")
                                                .executes(context -> {
                                                    ShahedConfig.setWhitelistEnabled(true);
                                                    context.getSource()
                                                            .sendSuccess(() -> Component
                                                                    .translatable(
                                                                            "command.ata.config.whitelist.enabled")
                                                                    .withStyle(s -> s.withColor(0x55FF55)), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("disable")
                                                .executes(context -> {
                                                    ShahedConfig.setWhitelistEnabled(false);
                                                    context.getSource()
                                                            .sendSuccess(() -> Component
                                                                    .translatable(
                                                                            "command.ata.config.whitelist.disabled")
                                                                    .withStyle(s -> s.withColor(0xFF5555)), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    ShahedConfig.reload();
                                    context.getSource()
                                            .sendSuccess(() -> Component.translatable("command.ata.config.reload")
                                                    .withStyle(s -> s.withColor(0x55FF55)), true);
                                    return 1;
                                })))
                .then(Commands.literal("map")
                        .then(Commands.literal("rescan")
                                .executes(context -> {
                                    ServerLevel level = context.getSource().getLevel();
                                    WorldMapData mapData = WorldMapData.get(level);
                                    mapData.clear();
                                    WorldMapScanner.getInstance().startScan(level);
                                    context.getSource().sendSuccess(() -> Component.literal("Map rescan started")
                                            .withStyle(s -> s.withColor(0x55FF55)), true);
                                    return 1;
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    WorldMapScanner scanner = WorldMapScanner.getInstance();
                                    ServerLevel level = context.getSource().getLevel();
                                    WorldMapData mapData = WorldMapData.get(level);

                                    if (scanner.isScanning()) {
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                String.format("Scanning: %d/%d chunks (%.1f%%)",
                                                        scanner.getProgress(), scanner.getTotalChunks(),
                                                        scanner.getProgressPercent()))
                                                .withStyle(s -> s.withColor(0xFFFF55)), false);
                                    } else if (mapData.isScanComplete()) {
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                String.format("Scan complete: %d coastline points",
                                                        mapData.getCoastlinePointCount()))
                                                .withStyle(s -> s.withColor(0x55FF55)), false);
                                    } else {
                                        context.getSource().sendSuccess(() -> Component.literal("Map not scanned")
                                                .withStyle(s -> s.withColor(0xFF5555)), false);
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("debug")
                        .then(Commands.literal("hitbox")
                                .then(Commands.literal("on")
                                        .executes(context -> setHitboxDebug(context.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> setHitboxDebug(context.getSource(), false))))
                        .then(Commands.literal("nolimits")
                                .then(Commands.literal("on")
                                        .executes(context -> setNoLaunchLimits(context.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> setNoLaunchLimits(context.getSource(), false))))
                        .then(Commands.literal("break")
                                .then(Commands.argument("part", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(PART_IDS, b))
                                        .executes(context -> breakPart(context.getSource(),
                                                StringArgumentType.getString(context, "part")))))
                        .then(Commands.literal("damage")
                                .then(Commands.argument("part", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(PART_IDS, b))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                                                .executes(context -> damagePart(context.getSource(),
                                                        StringArgumentType.getString(context, "part"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("fuze")
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(FUZE_IDS, b))
                                        .executes(context -> setFuze(context.getSource(),
                                                StringArgumentType.getString(context, "state")))))
                        .then(Commands.literal("heal")
                                .executes(context -> healTarget(context.getSource())))
                        .then(Commands.literal("release")
                                .executes(context -> releaseTarget(context.getSource(), 250))
                                .then(Commands.argument("range", IntegerArgumentType.integer(30, 4000))
                                        .executes(context -> releaseTarget(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "range")))))
                        .then(Commands.literal("parts")
                                .executes(context -> listParts(context.getSource())))
                        .then(Commands.literal("spawn")
                                .executes(context -> spawnHeldShahed(context.getSource(), 20.0, 3.0))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(2, 200))
                                        .executes(context -> spawnHeldShahed(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "distance"), 3.0))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(-64, 320))
                                                .executes(context -> spawnHeldShahed(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "distance"),
                                                        IntegerArgumentType.getInteger(context, "height"))))))));

        dispatcher.register(Commands.literal("shahed")
                .requires(source -> source.hasPermission(2))
                .redirect(root));
    }

    /**
     * Lifts every launch restriction: minimum and maximum range, blacklist and whitelist zones, and
     * the launcher's firing arc. Server-wide, and broadcast so the terminal screens stop refusing
     * launches the server would now accept.
     */
    private static int setNoLaunchLimits(CommandSourceStack source, boolean enabled) {
        com.synarsis.airtacticalarsenal.debug.DebugOverrides.setNoLaunchLimits(enabled);
        PacketDistributor.sendToAllPlayers(
                new com.synarsis.airtacticalarsenal.network.DebugOverridesPacket(enabled));
        source.sendSuccess(() -> Component.literal("Launch limits (range, zones, firing arc): ")
                .append(onOff(!enabled)), true);
        return 1;
    }

    private static final String[] PART_IDS = java.util.Arrays.stream(HitPart.values())
            .map(HitPart::id).toArray(String[]::new);
    private static final String[] FUZE_IDS = java.util.Arrays.stream(FuzeState.values())
            .map(FuzeState::id).toArray(String[]::new);

    /** The drone the caller means: the one they are looking at, or failing that the nearest. */
    private static ShahedEntity targetedShahed(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(160.0));

        ShahedEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (ShahedEntity shahed : source.getLevel().getEntitiesOfClass(ShahedEntity.class,
                new AABB(eye, far).inflate(4.0))) {
            HitScan.Hit hit = HitScan.nearest(shahed.getObbs(), eye, far);
            if (hit != null && hit.t() < bestT) {
                bestT = hit.t();
                best = shahed;
            }
        }
        if (best != null) {
            return best;
        }

        // Not aiming at one: fall back to whatever is closest, so these still work when the drone is
        // behind you or already spiralling out of view.
        return source.getLevel().getEntitiesOfClass(ShahedEntity.class,
                        player.getBoundingBox().inflate(96.0)).stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }

    private static HitPart parsePart(String id) {
        for (HitPart part : HitPart.values()) {
            if (part.id().equalsIgnoreCase(id)) {
                return part;
            }
        }
        return null;
    }

    private static boolean reject(CommandSourceStack source, ShahedEntity shahed, HitPart part, String rawPart) {
        if (shahed == null) {
            source.sendFailure(Component.literal("No Shahed in range. Try /ata debug spawn"));
            return true;
        }
        if (rawPart != null && part == null) {
            source.sendFailure(Component.literal("Unknown part '" + rawPart + "'. One of: "
                    + String.join(", ", PART_IDS)));
            return true;
        }
        return false;
    }

    private static int breakPart(CommandSourceStack source, String rawPart) throws CommandSyntaxException {
        ShahedEntity shahed = targetedShahed(source);
        HitPart part = parsePart(rawPart);
        if (reject(source, shahed, part, rawPart)) {
            return 0;
        }
        float left = shahed.partRemaining(part);
        if (left <= 0.0f) {
            source.sendSuccess(() -> Component.literal(part.id() + " is already out"), false);
            return 1;
        }
        shahed.debugDamagePart(part, left + 1.0f);
        source.sendSuccess(() -> Component.literal(
                part.id() + " knocked out  ->  " + shahed.getFallMode().id()), false);
        return 1;
    }

    private static int damagePart(CommandSourceStack source, String rawPart, int amount)
            throws CommandSyntaxException {
        ShahedEntity shahed = targetedShahed(source);
        HitPart part = parsePart(rawPart);
        if (reject(source, shahed, part, rawPart)) {
            return 0;
        }
        shahed.debugDamagePart(part, amount);
        source.sendSuccess(() -> Component.literal(String.format("%s  %.1f/%.1f  ->  %s",
                part.id(), shahed.partRemaining(part), shahed.partCapacity(part),
                shahed.getFallMode().id())), false);
        return 1;
    }

    /** Sets the warhead to a chosen condition. */
    private static int setFuze(CommandSourceStack source, String rawState) throws CommandSyntaxException {
        ShahedEntity shahed = targetedShahed(source);
        if (reject(source, shahed, null, null)) {
            return 0;
        }
        FuzeState wanted = null;
        for (FuzeState state : FuzeState.values()) {
            if (state.id().equalsIgnoreCase(rawState)) {
                wanted = state;
                break;
            }
        }
        if (wanted == null) {
            source.sendFailure(Component.literal("Unknown fuze state '" + rawState + "'. One of: "
                    + String.join(", ", FUZE_IDS)));
            return 0;
        }
        shahed.debugSetFuze(wanted);
        FuzeState now = shahed.getFuzeState();
        source.sendSuccess(() -> Component.literal("Fuze: " + now.id()), false);
        return 1;
    }

    private static int healTarget(CommandSourceStack source) throws CommandSyntaxException {
        ShahedEntity shahed = targetedShahed(source);
        if (reject(source, shahed, null, null)) {
            return 0;
        }
        shahed.debugRestore();
        source.sendSuccess(() -> Component.literal("Repaired and back under control"), false);
        return 1;
    }

    /** Lets a held drone go on a short flight ending at a point ahead of it. */
    private static int releaseTarget(CommandSourceStack source, int range) throws CommandSyntaxException {
        ShahedEntity shahed = targetedShahed(source);
        if (reject(source, shahed, null, null)) {
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 heading = shahed.getLookAngle();
        if (heading.horizontalDistanceSqr() < 1.0E-4) {
            double rad = Math.toRadians(shahed.getYRot());
            heading = new Vec3(Math.sin(rad), 0, Math.cos(rad));
        }
        Vec3 aim = shahed.position().add(heading.x * range, 0, heading.z * range);

        BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                BlockPos.containing(aim.x, shahed.getY(), aim.z));

        // Nose over for the last stretch rather than the stock 350 blocks, which on a short leg would
        // mean diving from the moment it is let go.
        shahed.debugCruiseTo(ground, Math.min(60.0, range * 0.25));
        shahed.setDebugHold(false);

        source.sendSuccess(() -> Component.literal(String.format(
                "Released  (%s)  ->  %d, %d, %d   %d blocks",
                shahed.getFallMode().id(), ground.getX(), ground.getY(), ground.getZ(), range)), false);
        return 1;
    }

    private static int listParts(CommandSourceStack source) throws CommandSyntaxException {
        ShahedEntity shahed = targetedShahed(source);
        if (reject(source, shahed, null, null)) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Shahed " + shahed.getId()
                        + "  mode: " + shahed.getFallMode().id()
                        + "  fuze: " + shahed.getFuzeState().id()
                        + (shahed.isWreck()
                                ? (shahed.isWreckArmed() ? "  WRECK (live charge)" : "  WRECK (inert)")
                                : ""))
                .withStyle(st -> st.withColor(0xFFFF55)), false);
        for (HitPart part : HitPart.values()) {
            float left = shahed.partRemaining(part);
            float cap = shahed.partCapacity(part);
            int colour = left <= 0 ? 0xFF5555 : (left < cap * 0.5f ? 0xFFAA00 : 0xAAAAAA);
            source.sendSuccess(() -> Component.literal(
                            String.format("  %-11s %5.1f / %5.1f", part.id(), left, cap))
                    .withStyle(st -> st.withColor(colour)), false);
        }
        return 1;
    }

    /** Puts a Shahed in the air in front of the caller and holds it there. */
    private static int spawnHeldShahed(CommandSourceStack source, double distance, double height)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        float yaw = player.getYRot();
        double rad = Math.toRadians(yaw);
        Vec3 at = player.position()
                .add(-Math.sin(rad) * distance, height + player.getEyeHeight(), Math.cos(rad) * distance);

        // Facing away from the caller, so the view is the one a defender actually gets.
        BlockPos nominalTarget = BlockPos.containing(at.add(-Math.sin(rad) * 500, 0, Math.cos(rad) * 500));
        ShahedEntity shahed = new ShahedEntity(level, at, nominalTarget, "low", yaw);
        shahed.isLegitSpawn = true;
        shahed.setDebugHold(true);
        shahed.setYRot(yaw);
        shahed.yRotO = yaw;
        shahed.setXRot(0.0f);
        shahed.xRotO = 0.0f;
        level.addFreshEntity(shahed);

        source.sendSuccess(() -> Component.literal(String.format(
                "Held Shahed at %.0f, %.0f, %.0f  (id %d)", at.x, at.y, at.z, shahed.getId())), false);
        return 1;
    }

    private static int setHitboxDebug(CommandSourceStack source, boolean enabled)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PacketDistributor.sendToPlayer(player, new HitboxDebugPacket(enabled));
        source.sendSuccess(() -> Component.literal("Hitbox wireframe: ").append(onOff(enabled)), false);
        return 1;
    }

}
