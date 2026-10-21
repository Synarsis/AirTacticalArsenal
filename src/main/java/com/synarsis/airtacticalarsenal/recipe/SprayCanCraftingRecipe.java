package com.synarsis.airtacticalarsenal.recipe;

import com.mojang.serialization.MapCodec;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

public class SprayCanCraftingRecipe extends ShapedRecipe {

    public SprayCanCraftingRecipe(ShapedRecipe base) {
        super(base.getGroup(), base.category(), base.pattern, base.getResultItem(null), base.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = this.getResultItem(registries).copy();

        if (result.is(ModItems.SPRAY_CAN.get())) {
            SprayCanItem.setCharge(result, SprayCanItem.MAX_CHARGE);
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    public static class Serializer implements RecipeSerializer<SprayCanCraftingRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        private static final MapCodec<SprayCanCraftingRecipe> CODEC =
                ShapedRecipe.Serializer.CODEC.xmap(SprayCanCraftingRecipe::new, recipe -> recipe);

        private static final StreamCodec<RegistryFriendlyByteBuf, SprayCanCraftingRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(SprayCanCraftingRecipe::new, recipe -> recipe);

        @Override
        public MapCodec<SprayCanCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SprayCanCraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
