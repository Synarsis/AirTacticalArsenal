package com.synarsis.airtacticalarsenal.compat;

import com.synarsis.airtacticalarsenal.ShahedMod;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class CreateBlockingHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!CreateCompat.isAnyCreateLoaded()) return;
        Player player = event.getEntity();
        if (!CreateCompat.isCreateFakePlayer(player)) return;

        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        var regKey = ForgeRegistries.BLOCKS.getKey(block);
        if (regKey != null && ShahedMod.MOD_ID.equals(regKey.getNamespace())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!CreateCompat.isAnyCreateLoaded()) return;
        Player player = event.getEntity();
        if (!CreateCompat.isCreateFakePlayer(player)) return;

        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        var regKey = ForgeRegistries.BLOCKS.getKey(block);
        if (regKey != null && ShahedMod.MOD_ID.equals(regKey.getNamespace())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!CreateCompat.isAnyCreateLoaded()) return;
        Player player = event.getEntity();
        if (!CreateCompat.isCreateFakePlayer(player)) return;

        Entity target = event.getTarget();
        var regKey = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (regKey != null && ShahedMod.MOD_ID.equals(regKey.getNamespace())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
