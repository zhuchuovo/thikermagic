package com.yg.thikermagic.energy;

import slimeknights.tconstruct.library.modifiers.ModifierEntry;

import java.util.Collection;

/**
 * Read-only view of the charge-driven combat bonuses a {@link ChargedAttackModule} was configured with.
 *
 * <p>This exists because Tinkers' own melee hooks are mergeable: once a second module on the same tool
 * implements {@link slimeknights.tconstruct.library.modifiers.hook.combat.MeleeDamageModifierHook}, the
 * hook instance handed back is an {@code AllMerger} rather than the module, so the attack speed tick
 * handler cannot read its numbers off the melee hook. A dedicated hook keeps that lookup type safe.
 *
 * <p>All values describe the bonus at a given charge; the charge itself is always read live from the
 * tool by the caller, since it moves as the tool is charged and discharged.
 */
public interface ChargedAttackHook {
  /** Extra melee damage, as a fraction of the damage that would otherwise be dealt, at a charge of 0-1. */
  default float damageBonus(ModifierEntry entry, float charge) {
    return 0;
  }

  /** Extra attack speed, as a fraction of the player's attack speed attribute, at a charge of 0-1. */
  default float attackSpeedBonus(ModifierEntry entry, float charge) {
    return 0;
  }

  /** Energy drained from the tool by one landed melee hit. */
  default int energyCost(ModifierEntry entry) {
    return 0;
  }

  /** Merges every module of one modifier into a single instance. */
  record AllMerger(Collection<ChargedAttackHook> modules) implements ChargedAttackHook {
    @Override
    public float damageBonus(ModifierEntry entry, float charge) {
      float total = 0;
      for (ChargedAttackHook module : modules) {
        total += module.damageBonus(entry, charge);
      }
      return total;
    }

    @Override
    public float attackSpeedBonus(ModifierEntry entry, float charge) {
      float total = 0;
      for (ChargedAttackHook module : modules) {
        total += module.attackSpeedBonus(entry, charge);
      }
      return total;
    }

    @Override
    public int energyCost(ModifierEntry entry) {
      // two modules charging for the same hit should not both bill the tool
      int cost = 0;
      for (ChargedAttackHook module : modules) {
        cost = Math.max(cost, module.energyCost(entry));
      }
      return cost;
    }
  }
}
