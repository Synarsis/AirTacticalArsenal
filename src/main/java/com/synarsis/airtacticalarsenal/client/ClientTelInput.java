package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientTelInput {

    private ClientTelInput() {
    }

    public static int poll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return 0;
        }
        Options o = mc.options;
        int mask = 0;
        if (o.keyUp.isDown()) {
            mask |= TelInput.FWD;
        }
        if (o.keyDown.isDown()) {
            mask |= TelInput.BACK;
        }
        if (o.keyLeft.isDown()) {
            mask |= TelInput.LEFT;
        }
        if (o.keyRight.isDown()) {
            mask |= TelInput.RIGHT;
        }
        if (o.keyJump.isDown()) {
            mask |= TelInput.BRAKE;
        }
        if (o.keySprint.isDown()) {
            mask |= TelInput.BOOST;
        }
        return mask;
    }
}
