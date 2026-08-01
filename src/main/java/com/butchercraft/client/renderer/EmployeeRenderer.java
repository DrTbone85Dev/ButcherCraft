package com.butchercraft.client.renderer;

import com.butchercraft.entity.employee.EmployeeEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class EmployeeRenderer extends HumanoidMobRenderer<EmployeeEntity, PlayerModel<EmployeeEntity>> {
    private static final ResourceLocation PLACEHOLDER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    public EmployeeRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(EmployeeEntity entity) {
        return PLACEHOLDER_TEXTURE;
    }
}
