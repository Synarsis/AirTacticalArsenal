package com.synarsis.airtacticalarsenal.client.renderer.item;

import com.synarsis.airtacticalarsenal.client.model.item.OrlanLauncherItemModel;
import com.synarsis.airtacticalarsenal.item.OrlanLauncherItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class OrlanLauncherItemRenderer extends GeoItemRenderer<OrlanLauncherItem> {

    public OrlanLauncherItemRenderer() {
        super(new OrlanLauncherItemModel());
    }
}
