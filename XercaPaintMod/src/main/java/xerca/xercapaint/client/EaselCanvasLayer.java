package xerca.xercapaint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import xerca.xercapaint.CanvasType;
import xerca.xercapaint.entity.EntityEasel;
import xerca.xercapaint.item.ItemCanvas;

public class EaselCanvasLayer extends RenderLayer<EntityEasel, EaselModel> {
    public EaselCanvasLayer(RenderLayerParent<EntityEasel, EaselModel> layerParent) {
        super(layerParent);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int i, EntityEasel entity, float v, float v1, float v2, float v3, float v4, float v5) {
        ItemStack itemstack = entity.getItem();
        if (itemstack.getItem() instanceof ItemCanvas itemCanvas) {
            poseStack.pushPose();

            CanvasType type = itemCanvas.getCanvasType();
            if (type == CanvasType.SMALL) {
                poseStack.scale(1.5F, 1.5f, 1.5f);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(-15.0F));
                poseStack.translate(-0.5, -1.17, -0.5);
            } else {
                // Centre the canvas on the easel for any size. The translate scales linearly with the
                // canvas dimensions; for 2x1 / 1x2 / 2x2 this reproduces the original hand-tuned values.
                float wScale = CanvasType.getWidth(type) / 16.0f;
                float hScale = CanvasType.getHeight(type) / 16.0f;
                poseStack.scale(2F, 2f, 2f);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(-15.0F));
                poseStack.translate(-0.74 + 0.145 * wScale, -0.815 - 0.1 * hScale, -0.5);
            }

            ModClient.getCanvasItemRenderer().renderByItem(itemstack, ItemDisplayContext.FIXED, poseStack, bufferSource, i, 0);

            poseStack.popPose();
        }
    }
}
