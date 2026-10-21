package com.synarsis.airtacticalarsenal.recipe;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AirTacticalArsenal.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<SprayCanFillingRecipe>> SPRAY_CAN_FILLING =
            RECIPE_SERIALIZERS.register("spray_can_filling", () -> SprayCanFillingRecipe.SERIALIZER);

    public static final DeferredHolder<RecipeSerializer<?>, SprayCanCraftingRecipe.Serializer> SPRAY_CAN_CRAFTING =
            RECIPE_SERIALIZERS.register("spray_can_crafting", () -> SprayCanCraftingRecipe.Serializer.INSTANCE);

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
