package xerca.xercapaint.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import xerca.xercapaint.Mod;
import xerca.xercapaint.item.ItemCanvas;
import xerca.xercapaint.item.Items;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class CanvasItemRenderer extends BlockEntityWithoutLevelRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final ResourceLocation BACK_LOCATION = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/birch_planks.png");
    private static final ResourceLocation EMPTY_CANVAS_LOCATION = Mod.id("textures/block/empty.png");

    public CanvasItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet entityModelSet) {
        super(dispatcher, entityModelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack matrixStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        if (stack.getItem() instanceof ItemCanvas itemCanvas) {
            // Only shrink oversized canvases down to a single slot in inventory/hotbar (GUI).
            // The easel reuses this renderer through the FIXED context and wants the full size.
            boolean guiNormalize = displayContext == ItemDisplayContext.GUI;
            boolean rendered = false;
            if (stack.get(Items.CANVAS_PIXELS) != null && RenderEntityCanvas.theInstance != null) {
                RenderEntityCanvas.Instance canvasIns = RenderEntityCanvas.theInstance.getCanvasRendererInstance(stack, itemCanvas.getWidth(), itemCanvas.getHeight());
                if (canvasIns != null) {
                    canvasIns.render(null, 0, 0, matrixStack, buffer, Direction.UP, combinedLight, guiNormalize);
                    rendered = true;
                }
            }

            if (!rendered) {
                renderEmptyCanvas(matrixStack, buffer, itemCanvas.getWidth(), itemCanvas.getHeight(), combinedLight, guiNormalize);
            }
        }
    }

    private void addVertex(VertexConsumer vb, Matrix4f m, PoseStack.Pose pose, double x, double y, double z, float tx, float ty, int lightmap, float xOff, float yOff, float zOff) {
        vb.addVertex(m, (float) x, (float) y, (float) z).setColor(255, 255, 255, 255).setUv(tx, ty).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightmap).setNormal(pose, xOff, yOff, zOff);
    }

    private void renderEmptyCanvas(PoseStack ms, MultiBufferSource buffer, float width, float height, int packedLight, boolean guiNormalize) {
        final float wScale = width / 16.0f;
        final float hScale = height / 16.0f;

        ms.pushPose();
        Matrix3f mn = ms.last().normal();

        float xOffset = Direction.UP.getStepX();
        float yOffset = Direction.UP.getStepY();
        float zOffset = Direction.UP.getStepZ();

        ms.last().normal().set(mn);

        float f = 1.0f / 32.0f;
        ms.translate(0.75, 0.5, 0.5);
        if (wScale > 1 || hScale > 1) {
            f /= 3.3f;
            if (guiNormalize) {
                // Cap the longest side at the 2-block footprint so 3x/4x canvases fit one slot
                // (and share the centering already tuned for the 2x models they reuse).
                f *= 2.0f / Math.max(wScale, hScale);
            }
        } else {
            f /= 2.0f;
        }

        ms.mulPose(Axis.YP.rotationDegrees(180));

        ms.scale(f, f, f);

        RenderSystem.setShaderTexture(0, EMPTY_CANVAS_LOCATION);

        Matrix4f m = ms.last().pose();
        PoseStack.Pose pose = ms.last();
        VertexConsumer vb = buffer.getBuffer(RenderType.entitySolid(EMPTY_CANVAS_LOCATION));

        // Draw the front
        addVertex(vb, m, pose, 0.0F, 32.0F * hScale, -1.0F, 1.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0F * wScale, 32.0F * hScale, -1.0F, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0F * wScale, 0.0F, -1.0F, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 0.0F, 0.0F, -1.0F, 1.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);

        vb = buffer.getBuffer(RenderType.entitySolid(BACK_LOCATION));
        // Draw the back and sides
        final float sideWidth = 1.0F / 16.0F;

        RenderSystem.setShaderTexture(0, BACK_LOCATION);
        addVertex(vb, m, pose, 0.0D, 0.0D, 1.0D, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 0.0D, 1.0D, 1.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 32.0D * hScale, 1.0D, 1.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 0.0D, 32.0D * hScale, 1.0D, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);

        // Sides
        addVertex(vb, m, pose, 0.0D, 0.0D, 1.0D, sideWidth, 0.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 0.0D, 32.0D * hScale, 1.0D, sideWidth, 1.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 0.0D, 32.0D * hScale, -1.0D, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 0.0D, 0.0D, -1.0D, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);

        addVertex(vb, m, pose, 0.0D, 32.0D * hScale, 1.0F, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 32.0D * hScale, 1.0F, 1.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 32.0D * hScale, -1.0F, 1.0F, sideWidth, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 0.0D, 32.0D * hScale, -1.0F, 0.0F, sideWidth, packedLight, xOffset, yOffset, zOffset);

        addVertex(vb, m, pose, 32.0D * wScale, 0.0D, -1.0F, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 32.0D * hScale, -1.0F, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 32.0D * hScale, 1.0F, sideWidth, 1.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 0.0D, 1.0F, sideWidth, 0.0F, packedLight, xOffset, yOffset, zOffset);

        addVertex(vb, m, pose, 0.0D, 0.0D, -1.0F, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 0.0D, -1.0F, 1.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 32.0D * wScale, 0.0D, 1.0F, 1.0F, 1.0F - sideWidth, packedLight, xOffset, yOffset, zOffset);
        addVertex(vb, m, pose, 0.0D, 0.0D, 1.0F, 0.0F, 1.0F - sideWidth, packedLight, xOffset, yOffset, zOffset);

        ms.popPose();
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        renderByItem(stack, displayContext, matrices, vertexConsumers, light, overlay);
    }
}
