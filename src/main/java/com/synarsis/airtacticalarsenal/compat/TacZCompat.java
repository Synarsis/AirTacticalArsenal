package com.synarsis.airtacticalarsenal.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

public class TacZCompat {

    private static boolean taczLoaded = false;
    private static boolean checkedTacz = false;

    public static boolean isTacZLoaded() {
        if (!checkedTacz) {
            taczLoaded = ModList.get().isLoaded("tacz");
            checkedTacz = true;
        }
        return taczLoaded;
    }

    public static boolean isTacZBullet(Entity entity) {
        if (!isTacZLoaded()) return false;

        String className = entity.getClass().getName();
        return className.contains("com.tacz.guns.entity.EntityKineticBullet");
    }

    public static ResourceLocation getGunId(Entity bullet) {
        if (!isTacZBullet(bullet)) return null;

        try {
            var method = bullet.getClass().getMethod("getGunId");
            return (ResourceLocation) method.invoke(bullet);
        } catch (Exception e) {
            return null;
        }
    }

    public static ResourceLocation getAmmoId(Entity bullet) {
        if (!isTacZBullet(bullet)) return null;

        try {
            var method = bullet.getClass().getMethod("getAmmoId");
            return (ResourceLocation) method.invoke(bullet);
        } catch (Exception e) {
            return null;
        }
    }

    public static Entity getShooter(Entity bullet) {
        if (!isTacZBullet(bullet)) return null;

        try {
            var method = bullet.getClass().getMethod("getOwner");
            return (Entity) method.invoke(bullet);
        } catch (Exception e) {
            return null;
        }
    }

    public static float getBulletDamage(Entity bullet, net.minecraft.world.phys.Vec3 hitPos) {
        if (!isTacZBullet(bullet)) return 10.0f;

        try {
            var method = bullet.getClass().getMethod("getDamage", net.minecraft.world.phys.Vec3.class);
            Object result = method.invoke(bullet, hitPos);
            if (result instanceof Float) {
                return (Float) result;
            } else if (result instanceof Number) {
                return ((Number) result).floatValue();
            }
        } catch (Exception e) {

            try {
                var field = bullet.getClass().getDeclaredField("damageAmount");
                field.setAccessible(true);
                Object value = field.get(bullet);
                if (value instanceof Number) {
                    return ((Number) value).floatValue();
                }
            } catch (Exception e2) {

            }
        }

        return 10.0f; 
    }

    public static String getGunIdString(Entity bullet) {
        ResourceLocation gunId = getGunId(bullet);
        return gunId != null ? gunId.toString() : null;
    }

    public static WeaponCategory classifyWeapon(String gunIdString) {
        if (gunIdString == null) return WeaponCategory.UNKNOWN;
        return categorizeWeapon(new ResourceLocation(gunIdString));
    }

    public enum WeaponCategory {
        PISTOL(0.6f),           
        SMG(0.8f),              
        RIFLE(1.0f),            
        BATTLE_RIFLE(1.3f),     
        SNIPER(1.8f),           
        HEAVY(2.5f),            
        EXPLOSIVE(5.0f),        
        UNKNOWN(1.0f);

        public final float damageMultiplier;

        WeaponCategory(float multiplier) {
            this.damageMultiplier = multiplier;
        }

        public float getDamageMultiplier() {
            return this.damageMultiplier;
        }
    }

    public static WeaponCategory categorizeWeapon(ResourceLocation gunId) {
        if (gunId == null) return WeaponCategory.UNKNOWN;

        String path = gunId.getPath().toLowerCase();

        if (path.contains("rpg") || path.contains("launcher") || path.contains("grenade") ||
            path.contains("rocket") || path.contains("missile") || path.contains("at4") ||
            path.contains("javelin") || path.contains("stinger") || path.contains("igla")) {
            return WeaponCategory.EXPLOSIVE;
        }

        if (path.contains("m2") || path.contains("dshk") || path.contains("kord") ||
            path.contains("nsv") || path.contains("browning") || path.contains("50cal") ||
            path.contains("12.7") || path.contains("127mm") || path.contains("heavy")) {
            return WeaponCategory.HEAVY;
        }

        if (path.contains("sniper") || path.contains("awp") || path.contains("awm") ||
            path.contains("svd") || path.contains("dragunov") || path.contains("m24") ||
            path.contains("remington") || path.contains("barrett") || path.contains("l96") ||
            path.contains("kar98") || path.contains("mosin") || path.contains("m82") ||
            path.contains("338") || path.contains("lapua") || path.contains("cheytac")) {
            return WeaponCategory.SNIPER;
        }

        if (path.contains("ak47") || path.contains("akm") || path.contains("ak-47") ||
            path.contains("g3") || path.contains("fal") || path.contains("scar-h") ||
            path.contains("scarh") || path.contains("m14") || path.contains("7.62") ||
            path.contains("762") || path.contains("pkm") || path.contains("rpk") ||
            path.contains("mg3") || path.contains("m60") || path.contains("m240")) {
            return WeaponCategory.BATTLE_RIFLE;
        }

        if (path.contains("m4") || path.contains("m16") || path.contains("ar15") ||
            path.contains("ar-15") || path.contains("hk416") || path.contains("scar-l") ||
            path.contains("scarl") || path.contains("aug") || path.contains("famas") ||
            path.contains("g36") || path.contains("ak74") || path.contains("ak-74") ||
            path.contains("5.56") || path.contains("556") || path.contains("5.45") ||
            path.contains("545") || path.contains("rifle") || path.contains("carbine")) {
            return WeaponCategory.RIFLE;
        }

        if (path.contains("smg") || path.contains("mp5") || path.contains("mp7") ||
            path.contains("p90") || path.contains("ump") || path.contains("vector") ||
            path.contains("ppsh") || path.contains("thompson") || path.contains("uzi") ||
            path.contains("mac10") || path.contains("mac-10") || path.contains("bizon") ||
            path.contains("pp19") || path.contains("mpx") || path.contains("submachine")) {
            return WeaponCategory.SMG;
        }

        if (path.contains("pistol") || path.contains("glock") || path.contains("m1911") ||
            path.contains("beretta") || path.contains("m9") || path.contains("usp") ||
            path.contains("p226") || path.contains("deagle") || path.contains("desert_eagle") ||
            path.contains("revolver") || path.contains("magnum") || path.contains("colt") ||
            path.contains("makarov") || path.contains("tt") || path.contains("tokarev") ||
            path.contains("9mm") || path.contains("45acp") || path.contains(".45") ||
            path.contains("handgun") || path.contains("sidearm")) {

            if (path.contains("deagle") || path.contains("desert_eagle") || path.contains("magnum")) {
                return WeaponCategory.SMG;
            }
            return WeaponCategory.PISTOL;
        }

        return WeaponCategory.RIFLE;
    }

    public static float calculateDamage(float baseDamage, ResourceLocation gunId, double distance) {
        WeaponCategory category = categorizeWeapon(gunId);
        float damage = baseDamage * category.damageMultiplier;

        if (category == WeaponCategory.EXPLOSIVE) {
            damage = Math.max(damage, 50.0f); 
        }

        return damage;
    }
}
