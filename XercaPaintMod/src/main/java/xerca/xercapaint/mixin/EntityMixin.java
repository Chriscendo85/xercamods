package xerca.xercapaint.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercapaint.DittoCompat;
import xerca.xercapaint.item.Items;

/**
 * Hides dropped XercaPaint items (as {@link ItemEntity}s) from players who aren't running the Delta
 * client, so they don't see a floating "vanilla" item they can't interact with. {@code broadcastToPlayer}
 * is the gate {@code ChunkMap.TrackedEntity.updatePlayer} uses to decide whether to send an entity to a
 * player. Pickup is blocked separately in {@link ItemEntityMixin} (pickup is proximity-based and doesn't
 * depend on the client seeing the entity).
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void xercapaint$hideDroppedPaintItems(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ItemEntity itemEntity
                && !DittoCompat.canSeeModdedEntities(player)
                && Items.isPaintItem(itemEntity.getItem())) {
            cir.setReturnValue(false);
        }
    }
}
