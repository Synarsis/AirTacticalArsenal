package com.synarsis.airtacticalarsenal.client.renderer.item;

import com.synarsis.airtacticalarsenal.client.model.item.OrlanItemModel;
import com.synarsis.airtacticalarsenal.item.OrlanItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class OrlanItemRenderer extends GeoItemRenderer<OrlanItem> {

    public OrlanItemRenderer() {
        super(new OrlanItemModel());
    }
}
