package com.synarsis.airtacticalarsenal.client.renderer.item;

import com.synarsis.airtacticalarsenal.client.model.item.SprayCanItemModel;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class SprayCanItemRenderer extends GeoItemRenderer<SprayCanItem> {

    public SprayCanItemRenderer() {
        super(new SprayCanItemModel());
    }
}
