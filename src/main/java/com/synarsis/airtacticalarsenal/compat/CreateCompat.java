package com.synarsis.airtacticalarsenal.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;

public class CreateCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("ATA-CreateCompat");

    private static boolean anyCreateDetected = false;
    private static boolean detectionDone = false;
    private static final Set<String> detectedModIds = new HashSet<>();
    private static final Set<String> createDamageNamespaces = new HashSet<>();

    private static final String[] KNOWN_CREATE_MODS = {
            "create",
            "create_connected",
            "create_dd",
            "create_stuff_additions",
            "create_new_age",
            "create_enchantment_industry",
            "create_things_and_misc",
            "create_cobblemon",
            "create_crafts_and_additions",
            "create_diesel_generators",
            "create_dreams_desires",
            "create_power_loader",
            "create_steam_rails",
            "create_deco",
            "create_garnished",
            "create_confectionery",
            "create_jetpack",
            "create_mechanical_extruder",
            "create_ore_excavation",
            "create_central_kitchen",
            "create_crystal_clear",
            "create_design_n_decor",
            "createaddition",
            "createbigcannons",
            "createoreexcavation",
            "railways",           
            "sliceanddice",       
            "createdieselgenerators",
            "creategoggles",
            "createindustry",
    };

    private static final String[] CREATE_PACKAGES = {
            "com.simibubi.create",
            "com.railwayteam.railways",
            "com.mrh0.createaddition",
            "com.hlysine.create_connected",
            "com.drmanganese.createenchantmentindustry",
            "com.github.createplus",
            "com.petrolpark",
            "net.createmod",
    };

    private static final String[] CREATE_DAMAGE_KEYS = {
            "crush",
            "cuckoo_surprise",
            "fan_fire",
            "fan_lava",
            "mechanical_drill",
            "mechanical_roller",
            "mechanical_saw",
            "potato_cannon",
            "run_over",
    };

    public static boolean isAnyCreateLoaded() {
        if (!detectionDone) {
            detect();
        }
        return anyCreateDetected;
    }

    private static void detect() {
        detectionDone = true;
        ModList modList = ModList.get();
        if (modList == null) return;

        for (String id : KNOWN_CREATE_MODS) {
            if (modList.isLoaded(id)) {
                detectedModIds.add(id);
                createDamageNamespaces.add(id);
            }
        }

        modList.getMods().forEach(modInfo -> {
            String modId = modInfo.getModId().toLowerCase(Locale.ROOT);
            if (modId.startsWith("create")) {
                detectedModIds.add(modId);
                createDamageNamespaces.add(modId);
            }
        });

        createDamageNamespaces.add("create");

        anyCreateDetected = !detectedModIds.isEmpty();

        if (anyCreateDetected) {
            LOGGER.info("[ATA] Обнаружены моды экосистемы Create: {}. Защита активирована.", detectedModIds);
        }
    }

    public static boolean isCreateDamageSource(DamageSource source) {
        if (!isAnyCreateLoaded()) return false;
        if (source == null) return false;

        try {
            Optional<ResourceKey<DamageType>> key = source.typeHolder().unwrapKey();
            if (key.isPresent()) {
                String namespace = key.get().location().getNamespace();
                if (createDamageNamespaces.contains(namespace)) return true;
                if (namespace.startsWith("create")) return true;
            }
        } catch (Exception ignored) {
        }

        try {
            Optional<ResourceKey<DamageType>> key = source.typeHolder().unwrapKey();
            if (key.isPresent()) {
                String path = key.get().location().getPath();
                String namespace = key.get().location().getNamespace();
                for (String createKey : CREATE_DAMAGE_KEYS) {
                    if (path.equals(createKey) && createDamageNamespaces.contains(namespace)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (isCreateRelatedEntity(source.getDirectEntity())) return true;
        if (isCreateRelatedEntity(source.getEntity())) return true;

        if (isCreateFakePlayer(source.getEntity())) return true;
        if (isCreateFakePlayer(source.getDirectEntity())) return true;

        return false;
    }

    public static boolean isCreateRelatedEntity(Entity entity) {
        if (entity == null) return false;
        if (!isAnyCreateLoaded()) return false;

        String className = entity.getClass().getName();
        for (String pkg : CREATE_PACKAGES) {
            if (className.startsWith(pkg)) return true;
        }

        try {
            var regKey = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (regKey != null) {
                String ns = regKey.getNamespace();
                if (createDamageNamespaces.contains(ns) || ns.startsWith("create")) return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    public static boolean isCreateFakePlayer(Entity entity) {
        if (entity == null) return false;
        if (!isAnyCreateLoaded()) return false;
        if (!(entity instanceof Player)) return false;

        String className = entity.getClass().getName();
        if (className.contains("DeployerFakePlayer")) return true;
        if (className.contains("SchematicWorld")) return true;

        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            String name = clazz.getName();
            for (String pkg : CREATE_PACKAGES) {
                if (name.startsWith(pkg)) return true;
            }
            clazz = clazz.getSuperclass();
        }

        return false;
    }

    public static void registerBlockProtection(Collection<Block> ataBlocks) {
        if (!isAnyCreateLoaded()) return;

        boolean registered = tryRegisterViaBlockMovementChecks(ataBlocks);

        if (!registered) {
            registered = tryRegisterViaContraptionMovementSetting(ataBlocks);
        }

        if (!registered) {
            LOGGER.warn("[ATA] Не удалось зарегистрировать блоки как неподвижные для Create. " +
                    "Блоки ATA могут быть перемещены контрапциями.");
        }
    }

    private static boolean tryRegisterViaBlockMovementChecks(Collection<Block> ataBlocks) {
        String[] apiPaths = {
                "com.simibubi.create.api.contraption.BlockMovementChecks",
                "net.createmod.api.contraption.BlockMovementChecks",
        };

        for (String apiPath : apiPaths) {
            try {
                Class<?> checksClass = Class.forName(apiPath);
                String basePkg = apiPath.substring(0, apiPath.lastIndexOf('.'));
                Class<?> checkInterface = Class.forName(basePkg + ".BlockMovementChecks$MovementAllowedCheck");
                Class<?> checkResultClass = Class.forName(basePkg + ".BlockMovementChecks$CheckResult");

                Object failResult = null;
                Object passResult = null;
                for (Object enumConst : checkResultClass.getEnumConstants()) {
                    String name = ((Enum<?>) enumConst).name();
                    if ("FAIL".equals(name)) failResult = enumConst;
                    if ("PASS".equals(name)) passResult = enumConst;
                }

                if (failResult == null || passResult == null) continue;

                Method registerMethod = checksClass.getMethod("registerMovementAllowedCheck", checkInterface);

                Set<Block> blockSet = new HashSet<>(ataBlocks);
                final Object fFail = failResult;
                final Object fPass = passResult;

                Object checkProxy = java.lang.reflect.Proxy.newProxyInstance(
                        checkInterface.getClassLoader(),
                        new Class<?>[]{checkInterface},
                        (proxy, method, args) -> {
                            if ("isMovementAllowed".equals(method.getName())) {
                                net.minecraft.world.level.block.state.BlockState state =
                                        (net.minecraft.world.level.block.state.BlockState) args[0];
                                if (blockSet.contains(state.getBlock())) {
                                    return fFail;
                                }
                                return fPass;
                            }
                            if ("toString".equals(method.getName())) return "ATA-BlockMovementCheck";
                            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                            if ("equals".equals(method.getName())) return proxy == args[0];
                            return null;
                        }
                );

                registerMethod.invoke(null, checkProxy);
                LOGGER.info("[ATA] BlockMovementChecks: {} блоков ATA помечены как UNMOVABLE (API: {})",
                        blockSet.size(), apiPath);
                return true;

            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                LOGGER.debug("[ATA] Ошибка при использовании {}: {}", apiPath, e.getMessage());
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tryRegisterViaContraptionMovementSetting(Collection<Block> ataBlocks) {
        String[] settingPaths = {
                "com.simibubi.create.api.contraption.ContraptionMovementSetting",
                "net.createmod.api.contraption.ContraptionMovementSetting",
        };

        for (String settingPath : settingPaths) {
            try {
                Class<?> settingClass = Class.forName(settingPath);
                Object unmovable = null;
                for (Object enumConst : settingClass.getEnumConstants()) {
                    if ("UNMOVABLE".equals(((Enum<?>) enumConst).name())) {
                        unmovable = enumConst;
                        break;
                    }
                }
                if (unmovable == null) continue;

                java.lang.reflect.Field registryField = null;
                for (java.lang.reflect.Field f : settingClass.getDeclaredFields()) {
                    if (f.getName().equals("REGISTRY") || f.getName().equals("ALL")) {
                        registryField = f;
                        break;
                    }
                }
                if (registryField == null) continue;

                registryField.setAccessible(true);
                Object registry = registryField.get(null);
                if (registry == null) continue;

                final Object finalUnmovable = unmovable;
                int count = 0;

                for (Block block : ataBlocks) {
                    boolean blockRegistered = false;

                    if (registry instanceof Map) {
                        ((Map) registry).put(block, finalUnmovable);
                        blockRegistered = true;
                    }

                    if (!blockRegistered) {
                        try {
                            Method m = registry.getClass().getMethod("register", Object.class, Object.class);
                            java.util.function.Supplier<?> supplier = () -> finalUnmovable;
                            m.invoke(registry, block, supplier);
                            blockRegistered = true;
                        } catch (NoSuchMethodException ignored) {}
                    }

                    if (!blockRegistered) {
                        try {
                            Method m = registry.getClass().getMethod("register", Block.class, settingClass);
                            m.invoke(registry, block, finalUnmovable);
                            blockRegistered = true;
                        } catch (NoSuchMethodException ignored) {}
                    }

                    if (blockRegistered) count++;
                }

                if (count > 0) {
                    LOGGER.info("[ATA] ContraptionMovementSetting: {} блоков ATA помечены как UNMOVABLE (API: {})",
                            count, settingPath);
                    return true;
                }

            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                LOGGER.debug("[ATA] Ошибка при использовании {}: {}", settingPath, e.getMessage());
            }
        }
        return false;
    }
}
