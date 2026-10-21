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
