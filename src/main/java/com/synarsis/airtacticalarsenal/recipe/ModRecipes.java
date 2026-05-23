package com.synarsis.airtacticalarsenal.recipe;

import com.synarsis.airtacticalarsenal.ShahedMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = 
        DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ShahedMod.MOD_ID);

    public static final RegistryObject<RecipeSerializer<SprayCanFillingRecipe>> SPRAY_CAN_FILLING = 
        RECIPE_SERIALIZERS.register("spray_can_filling", () -> SprayCanFillingRecipe.SERIALIZER);

    public static final RegistryObject<RecipeSerializer<SprayCanCraftingRecipe>> SPRAY_CAN_CRAFTING = 
        RECIPE_SERIALIZERS.register("spray_can_crafting", () -> SprayCanCraftingRecipe.Serializer.INSTANCE);

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
