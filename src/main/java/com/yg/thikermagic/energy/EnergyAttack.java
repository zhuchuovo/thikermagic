package com.yg.thikermagic.energy;

import com.yg.thikermagic.ThikerMagic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.capability.ToolEnergyCapability;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Glue for the charge-driven trait: the custom hook datapacks can read the trait's numbers through,
 * plus the two Tinkers' Construct registries the trait has to be installed into.
 *
 * <p>Attack speed is the odd one out. Tinkers' Construct turns a tool's melee stats into item
 * attribute modifiers when the tool is equipped, but those are built once per tool change and the
 * hook that feeds them explicitly warns that the list must not move between equipping and
 * unequipping. Energy moves constantly, so instead the charge-scaled part of the attack speed is
 * kept on the player as a transient attribute modifier and refreshed once per tick.
 */
public final class EnergyAttack {
  /** Modifier datapacks write for the trait, and the one this class looks for on a held tool. */
  public static final ModifierId MODIFIER_ID = new ModifierId(ThikerMagic.MOD_ID, "charged_core");

  /** Hook the trait exposes its configured numbers through. */
  public static final ModuleHook<ChargedAttackHook> HOOK = ModifierHooks.register(
      ResourceLocation.fromNamespaceAndPath(ThikerMagic.MOD_ID, "charged_attack"),
      ChargedAttackHook.class,
      ChargedAttackHook.AllMerger::new,
      new ChargedAttackHook() {});

  private static final ResourceLocation MODULE_ID =
      ResourceLocation.fromNamespaceAndPath(ThikerMagic.MOD_ID, "charged_attack");
  private static final ResourceLocation ATTACK_SPEED_MODIFIER_ID =
      ResourceLocation.fromNamespaceAndPath(ThikerMagic.MOD_ID, "charged_core_attack_speed");
  /**
   * The attack speed bonus is rounded to a 0.5% step, so {@code ATTACK_SPEED_STEP} is the number of
   * steps in one whole bonus point. {@link AttributeModifier} is a record, so an unchanged amount is
   * not marked dirty; without this rounding a tool sitting on a charger would resync the attribute
   * every tick for no visible change.
   */
  private static final double ATTACK_SPEED_STEP = 200d;

  private EnergyAttack() {}

  /** Installs the module and the attack speed tick hook. Called from the mod constructor. */
  public static void register() {
    ModifierModule.LOADER.register(MODULE_ID, ChargedAttackModule.LOADER);
    NeoForge.EVENT_BUS.addListener(EnergyAttack::onPlayerTick);
  }

  /** Fuel gauge reading for a tool: {@code energy / capacity}, clamped to 0-1. 0 when it holds no energy. */
  public static float charge(IToolStackView tool) {
    int capacity = ToolEnergyCapability.getMaxEnergy(tool);
    if (capacity <= 0) {
      return 0f;
    }
    return Mth.clamp(ToolEnergyCapability.getEnergy(tool) / (float)capacity, 0f, 1f);
  }

  /** Recomputes the transient attack speed modifier from whichever hand is holding the best charged tool. */
  private static void onPlayerTick(PlayerTickEvent.Post event) {
    Player player = event.getEntity();
    // the server owns the value; it is a syncable attribute, so the client picks it up from there
    if (player.level().isClientSide()) {
      return;
    }
    AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
    if (attackSpeed == null) {
      return;
    }

    // both hands are asked: the attribute cannot tell them apart, so the better tool wins
    double bonus = 0;
    for (InteractionHand hand : InteractionHand.values()) {
      bonus = Math.max(bonus, attackSpeedBonus(player.getItemInHand(hand)));
    }
    bonus = Math.round(bonus * ATTACK_SPEED_STEP) / ATTACK_SPEED_STEP;

    if (bonus <= 0) {
      attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER_ID);
    } else {
      attackSpeed.addOrUpdateTransientModifier(
          new AttributeModifier(ATTACK_SPEED_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
  }

  /** Charge-scaled attack speed bonus of one held stack, or 0 if it is not a charged tool. */
  private static double attackSpeedBonus(ItemStack stack) {
    // plain NBT scan first: most held items are not charged tools, and building a ToolStack for them
    // would re-parse the tool NBT twice per tick for nothing
    if (ModifierUtil.getModifierLevel(stack, MODIFIER_ID) <= 0) {
      return 0;
    }
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry entry = tool.getModifiers().getEntry(MODIFIER_ID);
    return entry.getHook(HOOK).attackSpeedBonus(entry, charge(tool));
  }
}
