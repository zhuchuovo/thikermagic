package com.yg.thikermagic;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Attributes that datapacks can point Tinkers' modifiers at.
 *
 * <p>All of them use a default of {@code 0}, so an entity that has none of them behaves exactly
 * like vanilla. Modifiers add to the value; the Ars Nouveau integration reads the total and either
 * applies it as a multiplier or hands it out as a flat amount, depending on the attribute.
 *
 * <p>The instance is registered onto the mod event bus by {@link ThikerMagic}.
 */
public final class ThikerMagicAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, ThikerMagic.MOD_ID);

    /**
     * Flat mana restored every tick, counted in mana points rather than as a ratio.
     * {@code 0.25} means "+0.25 mana every tick", which works out to 5 mana per second.
     *
     * <p>This is additive on purpose: it is added on top of whatever Ars Nouveau already calculated
     * for the player, and it scales linearly with the modifier level. A multiplier would instead
     * scale with the player's book tier and glyph count, which is how a tool could end up looking
     * slower as it levelled.
     */
    public static final DeferredHolder<Attribute, Attribute> MANA_REGEN = register("mana_regen", 100.0D);

    /** Percentage bonus to Ars Nouveau max mana. {@code 0.25} means +25%. */
    public static final DeferredHolder<Attribute, Attribute> MAX_MANA = register("max_mana", 100.0D);

    /** Fraction of the cast cooldown removed. {@code 0.4} means the cooldown lasts 60% as long. */
    public static final DeferredHolder<Attribute, Attribute> SPELL_COOLDOWN_REDUCTION = register("spell_cooldown_reduction", 1.0D);

    /**
     * Flat bonus to Ars Nouveau max mana, counted in mana points rather than as a ratio.
     * {@code 30} means "+30 mana". It is added before {@link #MAX_MANA}, so the two compose as
     * {@code (base + flat) * (1 + percent)} and a pack can read either half on its own.
     */
    public static final DeferredHolder<Attribute, Attribute> FLAT_MAX_MANA = register("flat_max_mana", 1000.0D);

    /**
     * Mana handed to the player the moment a piece of gear carrying it is equipped.
     *
     * <p>Unlike the other attributes this is a one-shot payout rather than a continuous value:
     * the integration reads it when equipment changes and adds it to current mana once.
     * {@code 20} means "+20 mana on equip".
     */
    public static final DeferredHolder<Attribute, Attribute> MANA_ON_EQUIP = register("mana_on_equip", 500.0D);

    public ThikerMagicAttributes() {}

    private static DeferredHolder<Attribute, Attribute> register(String name, double max) {
        return ATTRIBUTES.register(name, () -> new RangedAttribute(
                "attribute.name." + ThikerMagic.MOD_ID + "." + name, 0.0D, 0.0D, max).setSyncable(true));
    }

    @SubscribeEvent
    public void addPlayerAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, MANA_REGEN);
        event.add(EntityType.PLAYER, MAX_MANA);
        event.add(EntityType.PLAYER, SPELL_COOLDOWN_REDUCTION);
        event.add(EntityType.PLAYER, FLAT_MAX_MANA);
        event.add(EntityType.PLAYER, MANA_ON_EQUIP);
    }
}
