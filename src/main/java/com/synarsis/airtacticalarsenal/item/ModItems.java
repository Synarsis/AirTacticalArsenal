package com.synarsis.airtacticalarsenal.item;

import com.synarsis.airtacticalarsenal.ShahedMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ShahedMod.MOD_ID);

    public static final RegistryObject<Item> ISKANDER = ITEMS.register("iskander",
            () -> new IskanderItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> SHAHED = ITEMS.register("shahed",
            () -> new ShahedItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> LAUNCH_INTERFACE_CABLE = ITEMS.register("launch_interface_cable",
            () -> new LaunchInterfaceCableItem(new Item.Properties()
                    .stacksTo(16)));

    public static final RegistryObject<Item> SHAHED_LAUNCHER = ITEMS.register("shahed_launcher",
            () -> new ShahedLauncherItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> CONTROL_TABLET = ITEMS.register("control_tablet",
            () -> new ControlTabletItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> PAINT_STAND = ITEMS.register("paint_stand",
            () -> new PaintStandItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> SPRAY_CAN = ITEMS.register("spray_can",
            () -> new SprayCanItem(new Item.Properties()
                    .stacksTo(1)));

    public static final RegistryObject<Item> ORLAN_LAUNCHER = ITEMS.register("orlan_launcher",
            () -> new OrlanLauncherItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> ORLAN = ITEMS.register("orlan",
            () -> new OrlanItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> ORLAN_TABLET = ITEMS.register("orlan_tablet",
            () -> new OrlanTabletItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
