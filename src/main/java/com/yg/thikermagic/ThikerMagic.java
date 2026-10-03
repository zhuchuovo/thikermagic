package com.yg.thikermagic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Thiker Magic: a Tinkers' Construct addon whose modifiers drive Ars Nouveau stats.
 *
 * <p>Every modifier this mod ships is a plain datapack file under
 * {@code data/thikermagic/tinkering/modifiers/}, so other packs can reference the same ids,
 * retarget them with recipes, or hang them off their own tools.
 */
@Mod(ThikerMagic.MOD_ID)
public class ThikerMagic {
    public static final String MOD_ID = "thikermagic";
    public static final Logger LOG = LoggerFactory.getLogger("Thiker Magic");

    public ThikerMagic(IEventBus modBus, ModContainer container) {
        ThikerMagicAttributes.ATTRIBUTES.register(modBus);
        modBus.register(new ThikerMagicAttributes());
        container.registerConfig(ModConfig.Type.COMMON, ThikerMagicConfig.SPEC);

        // The charge-driven trait. Tinkers' Construct owns the energy storage itself; this adds the
        // module datapacks configure it with, the tick hook that keeps attack speed in step with the
        // charge, and the vanilla item sink used to fill tools in packs with no tech mod.
        com.yg.thikermagic.energy.EnergyAttack.register();
        com.yg.thikermagic.energy.EnergyCharging.register();

        // /thikermagic mana: reads the hooks back so a data pack problem is visible in game.
        NeoForge.EVENT_BUS.addListener(ThikerMagicCommand::register);

        // Ars Nouveau is an optional dependency. The integration class references its types
        // directly, so it must only be touched once we know the mod is actually loaded.
        if (ModList.get().isLoaded("ars_nouveau")) {
            try {
                com.yg.thikermagic.compat.ArsNouveauIntegration.init();
                LOG.info("Ars Nouveau detected - mana regen, max mana and cast cooldown hooks are live.");
            } catch (Throwable throwable) {
                LOG.error("Failed to attach the Ars Nouveau integration; attributes stay registered but inert.", throwable);
            }
        } else {
            LOG.info("Ars Nouveau is not installed - thikermagic attributes stay registered but have no effect.");
        }
    }

    /**
     * Readout for {@code /thikermagic mana}.
     *
     * <p>Kept here rather than in the command so the Ars Nouveau types stay behind the same
     * {@code isLoaded} guard as the hooks themselves.
     */
    public static List<Component> manaReadout(ServerPlayer player) {
        if (!ModList.get().isLoaded("ars_nouveau")) {
            return List.of(Component.literal(
                    "Ars Nouveau 未加载，只能查属性本身：/attribute @s thikermagic:max_mana get")
                    .withStyle(ChatFormatting.GRAY));
        }

        try {
            return com.yg.thikermagic.compat.ArsNouveauIntegration.describe(player);
        } catch (Throwable throwable) {
            LOG.error("Failed to build the /thikermagic mana readout", throwable);
            return List.of(Component.literal("读取魔艺数值失败：" + throwable).withStyle(ChatFormatting.RED));
        }
    }
}
