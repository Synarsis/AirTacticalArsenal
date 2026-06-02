package com.synarsis.airtacticalarsenal.client.renderer.item;

import com.synarsis.airtacticalarsenal.client.model.item.IskanderItemModel;
import com.synarsis.airtacticalarsenal.item.IskanderItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@OnlyIn(Dist.CLIENT)
public class IskanderItemRenderer extends GeoItemRenderer<IskanderItem> {

    public IskanderItemRenderer() {
        super(new IskanderItemModel());
    }
}
