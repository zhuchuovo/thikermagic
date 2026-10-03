package com.yg.thikermagic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/** Common config. Everything here is datapack/attribute driven at runtime; this only seeds defaults. */
public final class ThikerMagicConfig {
    public static final ModConfigSpec SPEC;
    public static final ThikerMagicConfig INSTANCE;

    /** Ticks of cooldown applied to the casting tool after a successful Ars Nouveau cast. */
    public final ModConfigSpec.IntValue castCooldownTicks;
    /** If true, casting while the tool is still on cooldown is blocked instead of silently ignored. */
    public final ModConfigSpec.BooleanValue blockCastOnCooldown;
    /**
     * If true, current mana is rescaled whenever the max mana cap moves, keeping the mana bar at the
     * same fill ratio. Ars Nouveau clamps current mana to the cap, so without this a cap increase
     * leaves the bar half empty and a cap decrease destroys the mana above the new cap.
     */
    public final ModConfigSpec.BooleanValue keepManaRatio;

    /** If true, a charging item held in the other hand can be spent to top up a charged tool. */
    public final ModConfigSpec.BooleanValue enableCharging;
    /** If true, charging only fires while sneaking, so a plain right click still reaches the tool. */
    public final ModConfigSpec.BooleanValue chargingRequiresSneak;
    /** Charging items, written as {@code "<item id>=<fe>"}. */
    public final ModConfigSpec.ConfigValue<List<? extends String>> chargingItems;

    static {
        Pair<ThikerMagicConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ThikerMagicConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private ThikerMagicConfig(ModConfigSpec.Builder builder) {
        builder.comment("Ars Nouveau cast cooldown").push("cast_cooldown");
        castCooldownTicks = builder
                .comment("Cooldown in ticks applied to the casting tool after a successful cast.",
                        "The thikermagic:spell_cooldown_reduction attribute shortens this value.",
                        "Set to 0 to leave Ars Nouveau casting completely untouched.")
                .defineInRange("cooldownTicks", 10, 0, 200);
        blockCastOnCooldown = builder
                .comment("If true, a cast attempted while the tool is still cooling down is cancelled.")
                .define("blockCastOnCooldown", true);
        builder.pop();

        builder.comment("Max mana").push("max_mana");
        keepManaRatio = builder
                .comment("Rescale current mana when the max mana cap changes, so equipping or removing gear",
                        "that grants thikermagic:max_mana or thikermagic:flat_max_mana keeps the mana bar at",
                        "the same fill ratio instead of halving it.",
                        "Set to false to let Ars Nouveau clamp current mana to the new cap instead.")
                .define("keepManaRatio", true);
        builder.pop();

        builder.comment("Charging tools that carry thikermagic:charged_core").push("energy_charging");
        enableCharging = builder
                .comment("If true, use a charging item from the other hand while sneaking to spend it for energy.",
                        "The tool is a plain Forge Energy item either way, so any charger from a tech mod can fill it",
                        "without this; the item sink exists for packs that have nothing to plug the tool into.")
                .define("enabled", true);
        chargingRequiresSneak = builder
                .comment("If true, charging only fires while sneaking, so a plain right click still reaches the tool.")
                .define("requiresSneak", true);
        chargingItems = builder
                .comment("Charging items, written as \"<item id>=<fe>\". The stack in the other hand is worth that much energy.")
                .defineList("items",
                        List.of("minecraft:redstone=1000",
                                "minecraft:redstone_block=9000",
                                "minecraft:glowstone_dust=2000",
                                "minecraft:amethyst_shard=4000"),
                        () -> "minecraft:redstone=1000",
                        value -> value instanceof String text && text.contains("="));
        builder.pop();
    }

    /**
     * Energy the given stack is worth as a charging item, or {@code 0} when it is not one.
     * Parsed on demand rather than cached, since this only runs on a right click.
     */
    public int energyValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String entry : chargingItems.get()) {
            int split = entry.indexOf('=');
            if (split <= 0) {
                continue;
            }
            if (id.equals(ResourceLocation.tryParse(entry.substring(0, split).trim()))) {
                try {
                    return Math.max(0, Integer.parseInt(entry.substring(split + 1).trim()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
