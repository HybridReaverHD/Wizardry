package com.teamwizardry.wizardry.common.item;

import com.teamwizardry.librarianlib.features.base.item.ItemMod;
import com.teamwizardry.wizardry.init.ModPotions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Created by HybridReaverHD.
 */
public class ItemManaPotion extends ItemMod {

	public ItemManaPotion() {
		super("mana_potion", "mana_potion", "mana_potion_corrupted", "mana_potion_empty");
		setMaxStackSize(16);
	}

	@Nonnull
	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
		ItemStack stack = player.getHeldItem(hand);
		if (getItemUseAction(stack) == EnumAction.BOW) {
			if (world.isRemote && (Minecraft.getMinecraft().currentScreen != null)) {
				return new ActionResult<>(EnumActionResult.FAIL, stack);
			} else {
				player.setActiveHand(hand);
				return new ActionResult<>(EnumActionResult.PASS, stack);
			}
		} else return new ActionResult<>(EnumActionResult.FAIL, stack);
	}

	@Nonnull
	@Override
	public EnumAction getItemUseAction(ItemStack stack) {
		return EnumAction.NONE;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) {
		return 10;
	}

	@Override
	public void onUsingTick(ItemStack stack, EntityLivingBase player, int count) {
		if (!(player instanceof EntityPlayer)) return;
		if (player.world.isRemote) return;

		if (count <= 1) {
			player.swingArm(player.getActiveHand());
			((EntityPlayer) player).getCooldownTracker().setCooldown(this, 30);

			if (stack.getItemDamage() == 2) { 			// corrupted mana potion
				player.addPotionEffect(new PotionEffect(ModPotions.MANA_REGEN, 200, 10));
				stack.setItemDamage(0);
			} else if (stack.getItemDamage() == 1) {	// mana potion
				player.addPotionEffect(new PotionEffect(ModPotions.MANA_REGEN, 200, 1));
				stack.setItemDamage(0);
			}
		}
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		if (stack.getItemDamage() == 3 && stack.hasTagCompound()) {
			if (stack.getItemDamage() == 2) {
				tooltip.add("So Ominous, Only a Madman will drink something like this");
			} else if (stack.getItemDamage() == 1) {
				tooltip.add("So Bubbly, I wonder what it tastes like...");
			}
		}
	}
}
