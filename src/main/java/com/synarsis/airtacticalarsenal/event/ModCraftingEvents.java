package com.synarsis.airtacticalarsenal.event;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Makes the Spray Can refill recipe count <em>stacked</em> black dye (e.g. 2 dye in a single grid
 * slot), not just one-per-slot.
 *
 * <p>Vanilla crafting only removes ONE item from each occupied slot, and {@code getRemainingItems}
 * cannot remove more (the leftover is grown back), so {@link com.synarsis.airtacticalarsenal.recipe.SprayCanFillingRecipe}
 * alone charges +30 per dye <em>slot</em>. After the craft, this handler tops the can's charge up
 * from the dye left over in the grid (the part of each stack beyond the one vanilla already took)
 * and consumes exactly those extra dyes — so a slot of 2 dye gives +60 and is fully spent.
 */
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
        // Only the refill recipe yields a partly-charged can (>=30). Skip empty/fresh cans and full ones
        // so we never silently eat dye left in the grid for an unrelated craft.
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
