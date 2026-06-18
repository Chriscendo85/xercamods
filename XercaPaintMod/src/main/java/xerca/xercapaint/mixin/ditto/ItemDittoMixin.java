package xerca.xercapaint.mixin.ditto;

import dev.delta.deltamod.ditto.DittoItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import xerca.xercapaint.item.ItemCanvas;
import xerca.xercapaint.item.ItemEasel;
import xerca.xercapaint.item.ItemPalette;

/**
 * Makes XercaPaint's items implement DeltaMod's {@link DittoItem} interface so ditto remaps them to a
 * harmless vanilla item for non-Delta-client players. This is applied <em>only when the {@code delta}
 * mod is present</em> (see {@code DittoMixinPlugin}); standalone, it's skipped and the item classes
 * never reference any delta class, so the mod runs fine on its own.
 */
@Mixin({ItemCanvas.class, ItemEasel.class, ItemPalette.class})
public abstract class ItemDittoMixin implements DittoItem {

    @Override
    public ItemStack getVanillaItemStack(ItemStack stack) {
        return new ItemStack(xercapaint$vanillaFallback(), stack.getCount());
    }

    @Override
    public Item getVanillaItem() {
        return xercapaint$vanillaFallback();
    }

    private Item xercapaint$vanillaFallback() {
        Object self = this;
        if (self instanceof ItemEasel) {
            return net.minecraft.world.item.Items.STICK;
        }
        if (self instanceof ItemPalette) {
            return net.minecraft.world.item.Items.BOWL;
        }
        return net.minecraft.world.item.Items.PAPER;
    }
}
