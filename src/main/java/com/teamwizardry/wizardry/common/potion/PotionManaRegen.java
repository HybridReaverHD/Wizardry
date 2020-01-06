package com.teamwizardry.wizardry.common.potion;

import com.teamwizardry.wizardry.api.NullMovementInput;
import com.teamwizardry.wizardry.api.capability.player.mana.ManaManager;
import com.teamwizardry.wizardry.init.ModPotions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.FoodStats;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Created by Demoniaque.
 */
public class PotionManaRegen extends PotionBase {
	static int base_mana_regen_per_tick = 10;
	static int base_hunger_decrease_per_tick = 1;

	public PotionManaRegen() {
		super("mana regen", false, 0x24ece7);
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
		// make player hungry
		player.getFoodStats().setFoodLevel(Math.max(player.getFoodStats().getFoodLevel() - amount_of_food_to_decrease, 0));
	}

}
