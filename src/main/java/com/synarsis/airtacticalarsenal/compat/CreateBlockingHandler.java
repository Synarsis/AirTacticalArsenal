package com.synarsis.airtacticalarsenal.compat;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID)
public class CreateBlockingHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!CreateCompat.isAnyCreateLoaded()) return;
        Player player = event.getEntity();
        if (!CreateCompat.isCreateFakePlayer(player)) return;

        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        var regKey = BuiltInRegistries.BLOCK.getKey(block);
        if (regKey != null && AirTacticalArsenal.MODID.equals(regKey.getNamespace())) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!CreateCompat.isAnyCreateLoaded()) return;
        Player player = event.getEntity();
        if (!CreateCompat.isCreateFakePlayer(player)) return;

        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        var regKey = BuiltInRegistries.BLOCK.getKey(block);
        if (regKey != null && AirTacticalArsenal.MODID.equals(regKey.getNamespace())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!CreateCompat.isAnyCreateLoaded()) return;
        Player player = event.getEntity();
        if (!CreateCompat.isCreateFakePlayer(player)) return;

        Entity target = event.getTarget();
        var regKey = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (regKey != null && AirTacticalArsenal.MODID.equals(regKey.getNamespace())) {
            event.setCanceled(true);
        }
    }
}
