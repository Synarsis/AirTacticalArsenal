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

/**
 * Shaped crafting recipe that produces a fully-charged Spray Can.
 *
 * <p>This mirrors the Forge 1.20.1 {@code SprayCanCraftingRecipe}, which extended
 * {@link ShapedRecipe} and overrode {@code assemble} to set the result's charge to
 * {@link SprayCanItem#MAX_CHARGE}. In MC 1.21 the shaped recipe is backed by a
 * {@code ShapedRecipePattern} and the serializer uses Codec / StreamCodec, so the
 * serializer simply wraps the vanilla shaped serializer and re-wraps the decoded
 * {@link ShapedRecipe} into this subclass (the Forge code delegated to
 * {@code RecipeSerializer.SHAPED_RECIPE} the same way).
 */
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

        // Reuse the vanilla shaped recipe codecs and re-wrap the result into our subclass.
        // PORT-NOTE: ShapedRecipe.Serializer.CODEC / STREAM_CODEC are accessed directly
        // (they are public static final in 1.21). SprayCanCraftingRecipe IS-A ShapedRecipe,
        // so the encode direction (SprayCanCraftingRecipe -> ShapedRecipe) is an identity cast.
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
