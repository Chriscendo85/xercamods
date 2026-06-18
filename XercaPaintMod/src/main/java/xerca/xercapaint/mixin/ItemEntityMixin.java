package xerca.xercapaint.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercapaint.DittoCompat;
import xerca.xercapaint.item.Items;

/**
 * Stops players who aren't running the Delta client from picking up XercaPaint's items (which ditto
 * shows them as a harmless vanilla item). Without this they could end up carrying a "secret" canvas
 * and even place an invisible painting with it. The dropped item is left on the ground for modded
 * players; see {@link EntityMixin} which also hides it from vanilla players' view.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Shadow
    public abstract ItemStack getItem();

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void xercapaint$blockVanillaPickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer
                && !DittoCompat.canSeeModdedEntities(serverPlayer)
                && Items.isPaintItem(getItem())) {
            ci.cancel();
        }
    }
}
