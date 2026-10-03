package com.yg.thikermagic.compat;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.api.util.ManaUtil;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.yg.thikermagic.ThikerMagicAttributes;
import com.yg.thikermagic.ThikerMagicConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges thikermagic attributes into Ars Nouveau.
 *
 * <p>This class touches Ars Nouveau types directly, so it is only loaded when
 * {@code ars_nouveau} is present (see {@code ThikerMagic}).
 */
public final class ArsNouveauIntegration {
    private ArsNouveauIntegration() {}

    public static void init() {
        NeoForge.EVENT_BUS.register(ArsNouveauIntegration.class);
    }

    /**
     * Ars Nouveau hands this event its own regen figure in mana <em>per second</em>, and divides it
     * down to whatever interval it pays out on. The thikermagic:mana_regen attribute is expressed in
     * mana <em>per tick</em>, so it is converted to the same per-second basis and simply added on
     * top: 0.25 on the attribute means 5 mana per second, or 0.25 mana every tick at 20 TPS.
     *
     * <p>Adding is deliberate. Multiplying used to make the bonus depend on the player's book tier
     * and glyph count, so the same tool restored a wildly different amount per level and could look
     * like it got slower; a flat amount always reads as "+N mana per tick" on the tooltip and always
     * lands as N mana per tick.
     */
    @SubscribeEvent
    public static void onManaRegen(ManaRegenCalcEvent event) {
        double perTick = getAttribute(event.getEntity(), ThikerMagicAttributes.MANA_REGEN);
        if (perTick > 0.0D) {
            event.setRegen(event.getRegen() + perTick * TICKS_PER_SECOND);
        }
    }

    /** Ars Nouveau measures mana regen per second; the attributes measure it per tick. */
    private static final double TICKS_PER_SECOND = 20.0D;

    /**
     * Applied after Ars Nouveau finished gear/glyph/tier math, so both bonuses stack on top.
     *
     * <p>The flat attribute is added first and the percentage second, giving
     * {@code (base + flat) * (1 + percent)}: a tool can hand out a floor of mana, and the
     * multiplier then scales it.
     */
    @SubscribeEvent
    public static void onMaxMana(MaxManaCalcEvent event) {
        double flat = getAttribute(event.getEntity(), ThikerMagicAttributes.FLAT_MAX_MANA);
        double bonus = getAttribute(event.getEntity(), ThikerMagicAttributes.MAX_MANA);
        if (flat <= 0.0D && bonus <= 0.0D) {
            return;
        }

        double value = (event.getMax() + flat) * (1.0D + bonus);
        event.setMax((int) Math.round(Math.min(value, Integer.MAX_VALUE)));
    }

    /* Equipment-change mana payout (thikermagic:mana_on_equip) */

    /** Players whose gear changed and still owe a payout. */
    private static final Set<UUID> PENDING_MANA_GRANTS = new HashSet<>();
    /** Game tick each player was last paid on, so swapping gear back and forth cannot be farmed. */
    private static final Map<UUID, Long> LAST_MANA_GRANT = new HashMap<>();
    /**
     * Last {@code thikermagic:mana_on_equip} total seen for each player.
     *
     * <p>Equipment changes fire for every slot, and they also fire when a player joins the world, so
     * swapping what is in your hand would otherwise re-pay a payout that belongs to the armor you
     * never took off. A payout only happens when the total actually grows.
     */
    private static final Map<UUID, Double> LAST_MANA_GRANT_VALUE = new HashMap<>();
    /** Ticks a player must wait between two payouts. */
    private static final long MANA_GRANT_COOLDOWN = 20L;

    /* Max-mana ratio keeper */

    /**
     * Last effective max mana seen for each player.
     *
     * <p>Ars Nouveau recomputes max mana every tick and clamps current mana to it on every write,
     * so gear carrying {@code thikermagic:max_mana} or {@code thikermagic:flat_max_mana} cannot be
     * equipped without the bar losing its fill ratio, and cannot be removed without destroying the
     * mana that no longer fits under the smaller cap. Scaling current mana by the same factor the
     * cap changed by keeps the ratio the player sees, so a full bar stays full.
     */
    private static final Map<UUID, Integer> LAST_MAX_MANA = new HashMap<>();

    /**
     * Equipment changes are noticed here, but the payout waits for the next server tick:
     * {@link LivingEntity} only refreshes its attribute map during its own tick, so reading
     * {@code thikermagic:mana_on_equip} right now would still return the pre-swap value.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getTo().isEmpty()) {
            return;
        }
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            PENDING_MANA_GRANTS.add(player.getUUID());
        }
    }

    /** Per-tick integration work: mana payouts first, then the max-mana ratio keeper. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        payPendingGrants(server);
        keepManaRatio(server);
    }

    /**
     * Pays {@code thikermagic:mana_on_equip} out to everyone whose gear changed last tick.
     *
     * <p>Only a rise in the attribute pays: putting the gear on counts, swapping an unrelated slot
     * does not. That keeps a one-shot refund from turning into a tap the player can open by moving
     * their held item around.
     */
    private static void payPendingGrants(MinecraftServer server) {
        if (PENDING_MANA_GRANTS.isEmpty()) {
            return;
        }

        Iterator<UUID> iterator = PENDING_MANA_GRANTS.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            iterator.remove();

            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                LAST_MANA_GRANT.remove(id);
                continue;
            }

            long now = player.level().getGameTime();
            Long last = LAST_MANA_GRANT.get(id);
            if (last != null && now - last < MANA_GRANT_COOLDOWN) {
                continue;
            }

            double amount = getAttribute(player, ThikerMagicAttributes.MANA_ON_EQUIP);
            Double previous = LAST_MANA_GRANT_VALUE.put(id, amount);
            if (amount <= 0.0D || (previous != null && amount <= previous)) {
                continue;
            }

            IManaCap mana = CapabilityRegistry.getMana(player);
            if (mana == null) {
                continue;
            }

            mana.addMana(amount);
            LAST_MANA_GRANT.put(id, now);
        }
    }

    /**
     * Rescales current mana whenever the max mana cap moves, so the bar keeps the fill ratio the
     * player was looking at instead of dropping to a fraction of itself.
     *
     * <p>Ars Nouveau refreshes the cap during {@code PlayerTickEvent.Pre} of the tick after the gear
     * change, and only clamps current mana on the regen tick after that. By running at the end of
     * the server tick, the new cap is already in place and the current value can be rescaled before
     * the clamp can eat it.
     */
    private static void keepManaRatio(MinecraftServer server) {
        if (!ThikerMagicConfig.INSTANCE.keepManaRatio.get()) {
            LAST_MAX_MANA.clear();
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ManaCap mana = CapabilityRegistry.getMana(player);
            if (mana == null) {
                continue;
            }

            int max = mana.getMaxMana();
            Integer last = LAST_MAX_MANA.put(player.getUUID(), max);
            if (last == null || last <= 0 || max <= 0 || last == max) {
                continue;
            }

            double current = mana.getCurrentMana();
            double scaled = Math.min(current * ((double) max / (double) last), max);
            if (scaled != current) {
                mana.setMana(scaled);
                mana.syncToClient(player);
            }
        }
    }

    /** Drops a departing player's bookkeeping so a reconnect cannot look like a gear swap. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        PENDING_MANA_GRANTS.remove(id);
        LAST_MANA_GRANT.remove(id);
        LAST_MANA_GRANT_VALUE.remove(id);
        LAST_MAX_MANA.remove(id);
    }

    /**
     * Ars Nouveau has no native cast cooldown, so thikermagic supplies one and the
     * {@code thikermagic:spell_cooldown_reduction} attribute shortens it.
     */
    @SubscribeEvent
    public static void onSpellCast(SpellCastEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }

        ItemStack tool = event.context.getCasterTool();
        if (tool.isEmpty()) {
            return;
        }

        ItemCooldowns cooldowns = player.getCooldowns();
        int base = ThikerMagicConfig.INSTANCE.castCooldownTicks.get();

        if (base <= 0) {
            return;
        }

        if (cooldowns.isOnCooldown(tool.getItem())) {
            if (ThikerMagicConfig.INSTANCE.blockCastOnCooldown.get()) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("thikermagic.message.cast_cooldown"), true);
            }
            return;
        }

        double reduction = Mth.clamp(getAttribute(player, ThikerMagicAttributes.SPELL_COOLDOWN_REDUCTION), 0.0D, 1.0D);
        int ticks = (int) Math.ceil(base * (1.0D - reduction));
        if (ticks > 0) {
            cooldowns.addCooldown(tool.getItem(), ticks);
        }
    }

    /**
     * Readout behind {@code /thikermagic mana}.
     *
     * <p>It reports both halves of every hook: what the thikermagic attributes currently add, and what
     * Ars Nouveau made of them. The max mana line also back-computes the cap the player would have
     * without these attributes, which is the quickest way to tell whether a data pack modifier is
     * actually reaching the attribute - a silently unloaded pack leaves both numbers identical.
     */
    public static List<Component> describe(ServerPlayer player) {
        List<Component> lines = new ArrayList<>();

        double flat = getAttribute(player, ThikerMagicAttributes.FLAT_MAX_MANA);
        double bonus = getAttribute(player, ThikerMagicAttributes.MAX_MANA);
        double regen = getAttribute(player, ThikerMagicAttributes.MANA_REGEN);
        double cooldown = getAttribute(player, ThikerMagicAttributes.SPELL_COOLDOWN_REDUCTION);
        double onEquip = getAttribute(player, ThikerMagicAttributes.MANA_ON_EQUIP);

        lines.add(line("词条属性", ChatFormatting.AQUA, String.format(
                "max_mana +%.0f%% · flat_max_mana +%s · mana_regen +%s/tick · 冷却 -%.0f%% · 穿戴回蓝 +%s",
                bonus * 100.0D, format(flat), format(regen), cooldown * 100.0D, format(onEquip))));

        IManaCap mana = CapabilityRegistry.getMana(player);
        if (mana == null) {
            lines.add(line("魔艺", ChatFormatting.RED, "读不到 IManaCap，上面只有 thikermagic 侧的数字。"));
            return lines;
        }

        ManaUtil.Mana calculated = ManaUtil.calcMaxMana(player);
        int max = calculated.Max();
        int usable = calculated.getRealMax();
        double bare = bonus > -1.0D ? (max / (1.0D + bonus)) - flat : max;
        lines.add(line("魔艺上限", ChatFormatting.AQUA, String.format(
                "法力 %s / %d（可用 %d，保留 %d%%）· 无词条时 %.0f · 词条净增 %+.0f",
                format(mana.getCurrentMana()), max, usable, Math.round(calculated.Reserve() * 100.0F),
                bare, max - bare)));

        double perSecond = ManaUtil.getManaRegen(player);
        double fromMods = regen * TICKS_PER_SECOND;
        lines.add(line("魔艺回蓝", ChatFormatting.AQUA, String.format(
                "%s/秒（裸装 %s + 词条 %s）· 词条折算每 tick %s",
                format(perSecond), format(perSecond - fromMods), format(fromMods), format(regen))));

        return lines;
    }

    /** One {@code Label: text} chat line, coloured by the caller. */
    private static Component line(String label, ChatFormatting color, String text) {
        return Component.literal(label + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(text).withStyle(color));
    }

    /** Trims the decimals off whole numbers so the readout stays readable. */
    private static String format(double value) {
        return DECIMALS.format(value);
    }

    private static final DecimalFormat DECIMALS = new DecimalFormat("#.##");

    private static double getAttribute(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }
}
