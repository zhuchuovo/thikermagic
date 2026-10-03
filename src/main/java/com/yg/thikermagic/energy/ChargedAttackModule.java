package com.yg.thikermagic.energy;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.ApiStatus.Internal;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.mantle.data.loadable.primitive.FloatLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MonsterMeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.capability.ToolEnergyCapability;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.utils.Util;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Turns the Forge Energy stored on a tool into melee output.
 *
 * <p>The capacity itself is not handled here. The datapack file pairs this module with
 * {@code tconstruct:stat_boost} on {@code tconstruct:max_energy} (10000 per level) and a
 * {@code tconstruct:trait} pointing at {@code tconstruct:energy_handler}. Between them, Tinkers'
 * Construct already exposes the tool as an {@code IEnergyStorage} and clamps the stored energy to
 * the capacity whenever the tool changes, so anything from a creative energy cell to a cable can
 * charge the tool.
 *
 * <p>This module only reads that stored value as {@code charge = energy / capacity}:
 * <ul>
 *   <li>melee damage is multiplied by {@code 1 + damage_bonus * charge}</li>
 *   <li>the attack speed attribute gets {@code attack_speed_bonus * charge}, applied by {@link EnergyAttack}</li>
 *   <li>every landed hit drains {@code energy_per_attack} from the tool</li>
 * </ul>
 *
 * <p>The bonuses are deliberately not level scaled: levelling the trait buys capacity, and full
 * charge is what buys damage and speed. All three numbers are still datapack fields, so a pack that
 * wants them to move with the level can set them there.
 *
 * <p>Both melee paths are hooked, not just the player one: when a mob swings a charged tool the
 * damage boost and the drain have to apply as well, matching how Tinkers' own combat modules
 * (lifesteal, fiery, decay) register on the monster hooks too.
 *
 * @param damageBonus      Extra melee damage at full charge, as a fraction of the dealt damage
 * @param attackSpeedBonus Extra attack speed at full charge, as a fraction of the player's attack speed
 * @param energyPerAttack  Energy drained by one landed melee hit
 */
public record ChargedAttackModule(float damageBonus, float attackSpeedBonus, int energyPerAttack)
    implements ModifierModule, ChargedAttackHook, MeleeDamageModifierHook, MeleeHitModifierHook,
    MonsterMeleeHitModifierHook, TooltipModifierHook {
  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.<ChargedAttackModule>defaultHooks(
      EnergyAttack.HOOK, ModifierHooks.MELEE_DAMAGE, ModifierHooks.MONSTER_MELEE_DAMAGE,
      ModifierHooks.MELEE_HIT, ModifierHooks.MONSTER_MELEE_HIT, ModifierHooks.TOOLTIP);

  public static final RecordLoadable<ChargedAttackModule> LOADER = RecordLoadable.create(
      FloatLoadable.ANY.defaultField("damage_bonus", 0.2f, ChargedAttackModule::damageBonus),
      FloatLoadable.ANY.defaultField("attack_speed_bonus", 0.5f, ChargedAttackModule::attackSpeedBonus),
      IntLoadable.ANY_FULL.defaultField("energy_per_attack", 100, ChargedAttackModule::energyPerAttack),
      ChargedAttackModule::new);

  /** @apiNote Internal constructor, the datapack loader is the only thing that builds this */
  @Internal
  public ChargedAttackModule {}

  @Override
  public float damageBonus(ModifierEntry entry, float charge) {
    return damageBonus * Mth.clamp(charge, 0f, 1f);
  }

  @Override
  public float attackSpeedBonus(ModifierEntry entry, float charge) {
    return attackSpeedBonus * Mth.clamp(charge, 0f, 1f);
  }

  @Override
  public int energyCost(ModifierEntry entry) {
    return Math.max(0, energyPerAttack);
  }

  @Override
  public float getMeleeDamage(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float baseDamage, float damage) {
    float bonus = damageBonus(modifier, EnergyAttack.charge(tool));
    return bonus > 0 ? damage * (1f + bonus) : damage;
  }

  @Override
  public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damageDealt) {
    drainEnergy(tool, modifier);
  }

  @Override
  public void onMonsterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage) {
    drainEnergy(tool, modifier);
  }

  /**
   * Drains one hit's worth of energy. Called from both melee paths — player swings run through
   * {@link #afterMeleeHit}, monster swings through {@link #onMonsterMeleeHit} — so a mob pays the
   * same price for the same bonus.
   */
  private void drainEnergy(IToolStackView tool, ModifierEntry modifier) {
    // drains on landed hits only, so a swing into thin air is free. addEnergy clamps at zero.
    int cost = energyCost(modifier);
    if (cost > 0 && ToolEnergyCapability.getEnergy(tool) > 0) {
      ToolEnergyCapability.addEnergy(tool, -cost);
    }
  }

  @Override
  public void addTooltip(IToolStackView tool, ModifierEntry entry, @Nullable Player player, List<Component> tooltip, TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
    float charge = EnergyAttack.charge(tool);
    tooltip.add(entry.getModifier().applyStyle(Component.translatable(
        "modifier.thikermagic.charged_core.readout",
        Util.PERCENT_FORMAT.format(damageBonus(entry, charge)),
        Util.PERCENT_FORMAT.format(attackSpeedBonus(entry, charge)))));
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  @Override
  public RecordLoadable<? extends ModifierModule> getLoader() {
    return LOADER;
  }
}
