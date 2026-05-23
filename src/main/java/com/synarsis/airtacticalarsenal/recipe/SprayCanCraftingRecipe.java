package com.synarsis.airtacticalarsenal.recipe;

import com.google.gson.JsonObject;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

public class SprayCanCraftingRecipe extends ShapedRecipe {

    private final ItemStack resultStack;

    public SprayCanCraftingRecipe(ResourceLocation id, String group, CraftingBookCategory category, 
                                   int width, int height, NonNullList<Ingredient> ingredients, ItemStack result) {
        super(id, group, category, width, height, ingredients, result);
        this.resultStack = result;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack result = this.resultStack.copy();

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

        @Override
        public SprayCanCraftingRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromJson(recipeId, json);
            return new SprayCanCraftingRecipe(recipeId, base.getGroup(), base.category(), 
                base.getWidth(), base.getHeight(), base.getIngredients(), 
                base.getResultItem(RegistryAccess.EMPTY));
        }

        @Override
        public SprayCanCraftingRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromNetwork(recipeId, buffer);
            return new SprayCanCraftingRecipe(recipeId, base.getGroup(), base.category(),
                base.getWidth(), base.getHeight(), base.getIngredients(),
                base.getResultItem(RegistryAccess.EMPTY));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SprayCanCraftingRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
