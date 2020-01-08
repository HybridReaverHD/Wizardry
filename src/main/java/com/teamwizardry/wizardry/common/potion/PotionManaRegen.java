package com.teamwizardry.wizardry.common.potion;

import com.teamwizardry.wizardry.api.capability.player.mana.ManaManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nonnull;

/**
 * Created by HybridReaverHD.
 */
public class PotionManaRegen extends PotionBase {
	static int base_mana_regen_per_tick = 10;
	static int base_hunger_decrease_per_tick = 1;

	public PotionManaRegen() {
		super("mana_regen", false, 0x24ece7);
	}

	@Override
	public boolean isReady(int duration, int amplifier) {
		return true;
	}

	@Override
	public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
		if (!hasEffect(entityLivingBaseIn)) return;					// only work on effected entities
		if (!(entityLivingBaseIn instanceof EntityPlayer)) return;	// only work on players

		EntityPlayer player = (EntityPlayer) entityLivingBaseIn;
		int amount_of_mana_to_add =  amplifier * base_mana_regen_per_tick;
		int amount_of_food_to_decrease =  amplifier * base_hunger_decrease_per_tick;

		// mana regen is a "gentle" effect and thus prevents mana overflow damage
		if (ManaManager.getMana(player) + amount_of_mana_to_add <= ManaManager.getMaxMana(player)) {
			ManaManager.forObject(player).setMana(ManaManager.getMana(player) + amount_of_mana_to_add).close();
		}
		// make player hungry (every power comes with a cost)
		player.getFoodStats().setFoodLevel(Math.max(player.getFoodStats().getFoodLevel() - amount_of_food_to_decrease, 0));
	}

}
