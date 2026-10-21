package com.synarsis.airtacticalarsenal.event;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.combat.hitbox.HitScan;
import com.synarsis.airtacticalarsenal.combat.hitbox.ObbProvider;
import com.synarsis.airtacticalarsenal.entity.tel.TelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID)
public final class ProjectileHitEvents {

    private ProjectileHitEvents() {
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) {
            return;
        }
        Entity target = entityHit.getEntity();
        if (target instanceof TelPart part) {
            target = part.getParent();
        }
        if (!(target instanceof ObbProvider provider) || provider.useAabb()) {
            return;
        }
        if (target.level().isClientSide) {
            return;
        }

        Projectile projectile = event.getProjectile();
        Vec3 to = projectile.position();
        Vec3 from = to.subtract(projectile.getDeltaMovement());

        if (HitScan.nearest(provider.getObbs(), from, to) == null) {
            event.setCanceled(true);
        }
    }
}
