package com.synarsis.airtacticalarsenal.recipe;

import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

public class SprayCanFillingRecipe extends CustomRecipe {

    public static final SimpleCraftingRecipeSerializer<SprayCanFillingRecipe> SERIALIZER = 
        new SimpleCraftingRecipeSerializer<>(SprayCanFillingRecipe::new);

    public SprayCanFillingRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack canStack = ItemStack.EMPTY;
        int dyeCount = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
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
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack canStack = ItemStack.EMPTY;
        int dyeCount = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.SPRAY_CAN.get())) {
                canStack = stack;
            } else if (stack.is(Items.BLACK_DYE)) {
                dyeCount += stack.getCount();
            }
        }

        if (canStack.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = new ItemStack(ModItems.SPRAY_CAN.get());

        int currentCharge = SprayCanItem.getCharge(canStack);
        int chargeNeeded = SprayCanItem.MAX_CHARGE - currentCharge;
        int dyesNeeded = (int) Math.ceil((float) chargeNeeded / 30);
        int dyesUsed = Math.min(dyeCount, dyesNeeded);
        int addCharge = dyesUsed * 30;
        SprayCanItem.setCharge(result, Math.min(SprayCanItem.MAX_CHARGE, currentCharge + addCharge));

        int extraConsume = dyesUsed - 1;
        if (extraConsume > 0) {
            result.getOrCreateTag().putInt("ExtraDyeConsume", extraConsume);
        }

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
