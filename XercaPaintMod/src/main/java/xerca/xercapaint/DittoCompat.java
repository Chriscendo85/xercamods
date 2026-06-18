package xerca.xercapaint;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridge to the "ditto" mod, which lets a backend server run modded content that vanilla
 * (non-Delta-client) players don't need installed: ditto rewrites the outgoing packets so
 * those players only ever see vanilla blocks/items.
 * <p>
 * ditto runs <em>server-side only</em> — it is not part of the Delta client modpack — so this
 * class never assumes ditto is present. When it isn't (e.g. on a client, or a server without
 * ditto) nothing is hidden and XercaPaint behaves like a normal mod.
 * <p>
 * ditto can remap modded <em>blocks</em> and <em>items</em> to vanilla equivalents, but it has no
 * concept of modded <em>entities</em>. XercaPaint's paintings and easels are entities, so instead of
 * remapping them we hide them entirely from players who aren't running the Delta client (see
 * {@code EntityCanvas}/{@code EntityEasel} {@code broadcastToPlayer}).
 */
public final class DittoCompat {
    private static final boolean DITTO_PRESENT = FabricLoader.getInstance().isModLoaded("ditto");

    private DittoCompat() {
    }

    /**
     * Returns whether the player may be sent XercaPaint's modded entities.
     *
     * @return true if ditto isn't installed (nothing is hidden) or the player is running the Delta
     *         client. Vanilla players on a ditto server return false so the entities are never
     *         tracked for / spawned to them.
     */
    public static boolean canSeeModdedEntities(ServerPlayer player) {
        return !DITTO_PRESENT || DittoHook.canSee(player);
    }

    /**
     * Isolates the direct reference to ditto in its own class so the JVM only loads it when ditto is
     * actually installed — keeping XercaPaint loadable on clients that don't ship ditto.
     */
    private static final class DittoHook {
        static boolean canSee(ServerPlayer player) {
            return !dev.delta.ditto.Ditto.INSTANCE.isVanillaPlayer(player);
        }
    }
}
