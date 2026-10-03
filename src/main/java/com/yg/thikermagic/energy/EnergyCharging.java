package com.yg.thikermagic.energy;

import com.yg.thikermagic.ThikerMagicConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import slimeknights.tconstruct.library.tools.capability.ToolEnergyCapability;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Item sink for the charge-driven trait, for packs with nothing to plug the tool into.
 *
 * <p>The tool itself is a plain {@code IEnergyStorage} (Tinkers' exposes every tool with a non-zero
 * {@code tconstruct:max_energy} through {@code ToolCapabilityProvider}), so any tech mod's charger,
 * battery or player charger can already fill it. This class only adds a vanilla way to do it:
 * sneak and use a charging item while holding the tool, and the item is spent for energy.
 */
public final class EnergyCharging {
  private EnergyCharging() {}

  /** Installs the two right click hooks. Called from the mod constructor. */
  public static void register() {
    NeoForge.EVENT_BUS.addListener(EnergyCharging::onRightClickItem);
    NeoForge.EVENT_BUS.addListener(EnergyCharging::onRightClickBlock);
  }

  private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
    if (charge(event.getEntity(), event.getHand())) {
      event.setCanceled(true);
    }
  }

  private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    // the click is only eaten when it actually charged something, so blocks keep working otherwise
    if (charge(event.getEntity(), event.getHand())) {
      event.setUseBlock(TriState.FALSE);
      event.setUseItem(TriState.FALSE);
      event.setCanceled(true);
    }
  }

  /**
   * Spends the charging item in the other hand to top up the tool in this hand.
   *
   * @return true when the tool took energy, meaning the interaction should be swallowed
   */
  private static boolean charge(Player player, InteractionHand hand) {
    if (!ThikerMagicConfig.INSTANCE.enableCharging.get() || player.level().isClientSide()) {
      return false;
    }
    if (ThikerMagicConfig.INSTANCE.chargingRequiresSneak.get() && !player.isShiftKeyDown()) {
      return false;
    }

    ItemStack stack = player.getItemInHand(hand);
    if (ModifierUtil.getModifierLevel(stack, EnergyAttack.MODIFIER_ID) <= 0) {
      return false;
    }
    ItemStack fuel = player.getItemInHand(
        hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    int value = ThikerMagicConfig.INSTANCE.energyValue(fuel);
    if (value <= 0) {
      return false;
    }

    ToolStack tool = ToolStack.from(stack);
    int capacity = ToolEnergyCapability.getMaxEnergy(tool);
    if (capacity <= 0) {
      return false;
    }
    int stored = ToolEnergyCapability.getEnergy(tool);
    if (stored >= capacity) {
      player.displayClientMessage(
          Component.translatable("thikermagic.message.charge.full").withStyle(ChatFormatting.GRAY), true);
      return true;
    }

    // the item is spent for what actually fits, so a nearly full tool does not waste a block
    int gained = Math.min(value, capacity - stored);
    ToolEnergyCapability.addEnergy(tool, gained);
    if (!player.getAbilities().instabuild) {
      fuel.shrink(1);
    }
    player.displayClientMessage(Component.translatable("thikermagic.message.charge.gained", gained,
        ToolEnergyCapability.MAX_STAT.formatContents(ToolEnergyCapability.getEnergy(tool), capacity)), true);
    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.6f);
    return true;
  }
}
