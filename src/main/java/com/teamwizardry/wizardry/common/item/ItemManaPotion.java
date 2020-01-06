package com.teamwizardry.wizardry.common.item;

import com.teamwizardry.librarianlib.core.LibrarianLib;
import com.teamwizardry.librarianlib.features.base.item.ItemMod;
import com.teamwizardry.librarianlib.features.helpers.NBTHelper;
import com.teamwizardry.librarianlib.features.utilities.client.TooltipHelper;
import com.teamwizardry.wizardry.api.capability.player.mana.ManaManager;
import com.teamwizardry.wizardry.common.block.fluid.ModFluids;
import com.teamwizardry.wizardry.common.core.DamageSourceMana;
import com.teamwizardry.wizardry.init.ModPotions;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Created by Demoniaque.
 */
public class ItemManaPotion extends ItemMod {

	public ItemManaPotion() {
		super("mana_potion", "empty potion", "mana potion", "currupted mana potion");
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

			if (stack.getItemDamage() == 2) { 			// currpted mana potion
				player.addPotionEffect(new PotionEffect(ModPotions.MANA_REGEN, 200, 10));
				stack.setItemDamage(0);
			} else if (stack.getItemDamage() == 1) {	// mana potion
				player.addPotionEffect(new PotionEffect(ModPotions.MANA_REGEN, 200, 1));
				stack.setItemDamage(0);
			}
		}
	}

	@Override
	public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
		//if (stack.getItemDamage() == 0) {
		//	entity.attackEntityFrom(DamageSource.causePlayerDamage(player), 2);
		//	stack.setItemDamage(3);
		//	if (entity instanceof EntityPlayer)
		//		NBTHelper.setUUID(stack, "uuid", entity.getUniqueID());
		//	else NBTHelper.setString(stack, "entity", entity.getName());
		//}
		return false;
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		String desc = stack.getTranslationKey() + ".desc";
		String used = LibrarianLib.PROXY.canTranslate(desc) ? desc : desc + "0";
		if (LibrarianLib.PROXY.canTranslate(used)) {
			TooltipHelper.addToTooltip(tooltip, used);
			int i = 0;
			while (LibrarianLib.PROXY.canTranslate(desc + (++i)))
				TooltipHelper.addToTooltip(tooltip, desc + i);
		}

		if (stack.getItemDamage() == 3 && stack.hasTagCompound()) {
			if (stack.getItemDamage() == 2) {
				tooltip.add("So Ominous, Only a Madman will drink something like this");
			} else if (stack.getItemDamage() == 1) {
				tooltip.add("So Bubbly, I wonder what it tastes like...");
			}
		}
	}
}
