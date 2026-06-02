package com.synarsis.airtacticalarsenal.item;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AirTacticalArsenal.MODID);

    public static final DeferredItem<Item> ISKANDER = ITEMS.register("iskander",
            () -> new IskanderItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final DeferredItem<Item> SHAHED = ITEMS.register("shahed",
            () -> new ShahedItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final DeferredItem<Item> LAUNCH_INTERFACE_CABLE = ITEMS.register("launch_interface_cable",
            () -> new LaunchInterfaceCableItem(new Item.Properties()
                    .stacksTo(16)));

    public static final DeferredItem<Item> SHAHED_LAUNCHER = ITEMS.register("shahed_launcher",
            () -> new ShahedLauncherItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final DeferredItem<Item> CONTROL_TABLET = ITEMS.register("control_tablet",
            () -> new ControlTabletItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final DeferredItem<Item> PAINT_STAND = ITEMS.register("paint_stand",
            () -> new PaintStandItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> SPRAY_CAN = ITEMS.register("spray_can",
            () -> new SprayCanItem(new Item.Properties()
                    .stacksTo(1)));

    public static final DeferredItem<Item> ORLAN_LAUNCHER = ITEMS.register("orlan_launcher",
            () -> new OrlanLauncherItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final DeferredItem<Item> ORLAN = ITEMS.register("orlan",
            () -> new OrlanItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final DeferredItem<Item> ORLAN_TABLET = ITEMS.register("orlan_tablet",
            () -> new OrlanTabletItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
