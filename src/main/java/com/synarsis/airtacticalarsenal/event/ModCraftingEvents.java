package com.synarsis.airtacticalarsenal.event;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModCraftingEvents {

    private static final int CHARGE_PER_DYE = 30;

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (!(result.getItem() instanceof SprayCanItem)) {
            return;
        }

        int charge = SprayCanItem.getCharge(result);
        if (charge <= 0 || charge >= SprayCanItem.MAX_CHARGE) {
            return;
        }

        Container grid = event.getInventory();
        int leftoverDye = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.is(Items.BLACK_DYE)) {
                leftoverDye += s.getCount();
            }
        }
        if (leftoverDye <= 0) {
            return;
        }

        int dyesNeeded = (int) Math.ceil((SprayCanItem.MAX_CHARGE - charge) / (double) CHARGE_PER_DYE);
        int extraDyes = Math.min(leftoverDye, dyesNeeded);
        if (extraDyes <= 0) {
            return;
        }

        SprayCanItem.setCharge(result, Math.min(SprayCanItem.MAX_CHARGE, charge + extraDyes * CHARGE_PER_DYE));

        int toConsume = extraDyes;
        for (int i = 0; i < grid.getContainerSize() && toConsume > 0; i++) {
            ItemStack s = grid.getItem(i);
            if (s.is(Items.BLACK_DYE)) {
                int take = Math.min(s.getCount(), toConsume);
                grid.removeItem(i, take);
                toConsume -= take;
            }
        }
    }
}
