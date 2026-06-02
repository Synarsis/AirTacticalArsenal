package com.synarsis.airtacticalarsenal.recipe;

import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Special (shapeless) crafting recipe that tops up a water-filled Spray Can with black dye.
 * Each black dye consumed adds {@code 30} charge (10%), up to {@link SprayCanItem#MAX_CHARGE}.
 *
 * <p>Ported from the Forge 1.20.1 {@code SprayCanFillingRecipe} (a {@link CustomRecipe}
 * registered through {@link SimpleCraftingRecipeSerializer}). The matching logic is preserved:
 * exactly one Spray Can that already {@link SprayCanItem#hasWater(ItemStack) has water} and is
 * not yet full, plus one or more black dye, and nothing else.
 *
 * <p>PORT-NOTE (dye consumption): The Forge version wrote an {@code "ExtraDyeConsume"} NBT int
 * onto the result and a separate {@code PlayerEvent.ItemCraftedEvent} handler in the main mod
 * class shrank that many additional dyes after the craft. In MC 1.21 raw item NBT no longer
 * exists (data components only) and the proper hook for removing crafting inputs is
 * {@link #getRemainingItems(CraftingInput)} — but the vanilla crafting flow already removes
 * exactly one item from every occupied grid slot, and {@code getRemainingItems} cannot remove
 * MORE than that from a single slot (it can only add leftovers back). The original
 * cross-slot "consume N from one stack" behaviour is therefore not expressible inside the
 * recipe alone, and that post-craft event handler was not ported to NeoForge.
 *
 * <p>To stay self-contained (no NBT, no extra event handler) this port consumes exactly one
 * black dye per occupied dye slot — which is precisely what vanilla crafting removes — and
 * grants {@code 30} charge per dye consumed, capped at the charge actually needed and at
 * {@link SprayCanItem#MAX_CHARGE}. Net effect: place the can plus up to eight black dye (one
 * per remaining grid slot) to refill up to 240 charge in a single craft; stacking multiple
 * dye in one slot now only consumes one of them. If exact stack-consumption parity is
 * required, re-add an {@code ItemCraftedEvent}/{@code PlayerEvent.ItemCraftedEvent} handler
 * to the game event bus (e.g. in event/ServerForgeEvents.java).
 */
public class SprayCanFillingRecipe extends CustomRecipe {

    private static final int CHARGE_PER_DYE = 30;

    public static final SimpleCraftingRecipeSerializer<SprayCanFillingRecipe> SERIALIZER =
            new SimpleCraftingRecipeSerializer<>(SprayCanFillingRecipe::new);

    public SprayCanFillingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack canStack = ItemStack.EMPTY;
        int dyeCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.SPRAY_CAN.get())) {
                if (!canStack.isEmpty()) return false;
                canStack = stack;
            } else if (stack.is(Items.BLACK_DYE)) {
                dyeCount += stack.getCount();
            } else {
                return false;
            }
        }

        if (canStack.isEmpty()) return false;
        if (!SprayCanItem.hasWater(canStack)) return false;
        if (dyeCount < 1) return false;

        int currentCharge = SprayCanItem.getCharge(canStack);
        return currentCharge < SprayCanItem.MAX_CHARGE;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack canStack = ItemStack.EMPTY;
        int dyeSlots = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.SPRAY_CAN.get())) {
                canStack = stack;
            } else if (stack.is(Items.BLACK_DYE)) {
                // One dye is consumed per occupied slot by the vanilla crafting flow.
                dyeSlots++;
            }
        }

        if (canStack.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = new ItemStack(ModItems.SPRAY_CAN.get());

        int currentCharge = SprayCanItem.getCharge(canStack);
        int chargeNeeded = SprayCanItem.MAX_CHARGE - currentCharge;
        int dyesNeeded = (int) Math.ceil((float) chargeNeeded / CHARGE_PER_DYE);
        int dyesUsed = Math.min(dyeSlots, dyesNeeded);
        int addCharge = dyesUsed * CHARGE_PER_DYE;
        SprayCanItem.setCharge(result, Math.min(SprayCanItem.MAX_CHARGE, currentCharge + addCharge));

        // Water is spent when topping up the charge (matches Forge behaviour).
        SprayCanItem.setHasWater(result, false);

        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
