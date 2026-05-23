package com.synarsis.airtacticalarsenal.client.renderer.item;

import com.synarsis.airtacticalarsenal.client.model.item.PaintStandItemModel;
import com.synarsis.airtacticalarsenal.item.PaintStandItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class PaintStandItemRenderer extends GeoItemRenderer<PaintStandItem> {

    public PaintStandItemRenderer() {
        super(new PaintStandItemModel());
    }
}
