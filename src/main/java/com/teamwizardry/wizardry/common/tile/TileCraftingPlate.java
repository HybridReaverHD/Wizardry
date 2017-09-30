package com.teamwizardry.wizardry.common.tile;

import com.teamwizardry.librarianlib.features.autoregister.TileRegister;
import com.teamwizardry.librarianlib.features.helpers.ItemNBTHelper;
import com.teamwizardry.librarianlib.features.network.PacketHandler;
import com.teamwizardry.librarianlib.features.saving.Save;
import com.teamwizardry.wizardry.api.Constants;
import com.teamwizardry.wizardry.api.block.TileManaInteracter;
import com.teamwizardry.wizardry.api.capability.CapManager;
import com.teamwizardry.wizardry.api.item.EnumPearlType;
import com.teamwizardry.wizardry.api.item.IInfusable;
import com.teamwizardry.wizardry.api.render.ClusterObject;
import com.teamwizardry.wizardry.api.spell.SpellBuilder;
import com.teamwizardry.wizardry.api.spell.module.Module;
import com.teamwizardry.wizardry.api.util.RandUtil;
import com.teamwizardry.wizardry.common.block.BlockCraftingPlate;
import com.teamwizardry.wizardry.common.network.PacketExplode;
import com.teamwizardry.wizardry.init.ModItems;
import com.teamwizardry.wizardry.init.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Created by Saad on 6/10/2016.
 */
@TileRegister("crafting_plate")
public class TileCraftingPlate extends TileManaInteracter {

	@Save
	public int craftingTime = 300;
	@Save
	public int craftingTimeLeft = 300;
	@Save
	public int tick;
	@Save
	public boolean isCrafting;
	@Save
	@Nullable
	public ItemStack output;
	public List<ClusterObject> inventory = new ArrayList<>();
	public Random random = new Random(getPos().toLong());

	public static final HashSet<BlockPos> poses = new HashSet<>();

	static {
		poses.add(new BlockPos(3, 2, 3));
		poses.add(new BlockPos(-3, 2, 3));
		poses.add(new BlockPos(3, 2, -3));
		poses.add(new BlockPos(-3, 2, -3));
	}

	public TileCraftingPlate() {
		super(10000, 10000);
	}

	@Override
	public void readCustomNBT(NBTTagCompound compound) {
		inventory.clear();
		NBTTagList list = compound.getTagList("clusters", NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			ClusterObject cluster = new ClusterObject(this);
			cluster.deserializeNBT(list.getCompoundTagAt(i));
			inventory.add(cluster);
		}
	}

	@Override
	public void writeCustomNBT(NBTTagCompound compound, boolean sync) {
		NBTTagList list = new NBTTagList();
		for (ClusterObject cluster : inventory) list.appendTag(cluster.serializeNBT());
		compound.setTag("clusters", list);
	}

	@Override
	public void update() {
		super.update();

		if (!((BlockCraftingPlate) getBlockType()).isStructureComplete(getWorld(), getPos())) return;

		if (!new CapManager(getCap()).isManaFull()) {
			for (BlockPos relative : poses) {
				BlockPos target = getPos().add(relative);
				TileEntity tile = world.getTileEntity(target);
				if (tile != null && tile instanceof TilePearlHolder) {
					if (!((TilePearlHolder) tile).isBenign) {
						((TilePearlHolder) tile).structurePos = getPos();
						tile.markDirty();
						world.notifyBlockUpdate(target, world.getBlockState(target), world.getBlockState(target), 3);
						((TilePearlHolder) tile).suckManaFrom(getWorld(), getPos(), getCap(), target, 100, false);
					}
				}
			}
		}

		for (EntityItem entityItem : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(2, 2, 2))) {
			ItemStack stack = entityItem.getItem().copy();
			stack.setCount(1);
			entityItem.getItem().shrink(1);
			inventory.add(new ClusterObject(this, stack, world, entityItem.getPositionVector().subtract(new Vec3d(pos))));
			markDirty();
		}

		if (world.isRemote) return;
		if (tick < 360) tick += 10;
		else tick = 0;
		if (!inventory.isEmpty()) {
			for (ClusterObject cluster : inventory) cluster.tick(world, random);
			world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
		}
		markDirty();

		if ((output == null) && !isCrafting && !inventory.isEmpty() && (inventory.get(inventory.size() - 1).stack.getItem() instanceof IInfusable)) {
			isCrafting = true;
			craftingTimeLeft = craftingTime;
			output = inventory.remove(inventory.size() - 1).stack;
			markDirty();
		}

		if (isCrafting) {
			if (!new CapManager(cap).isManaEmpty()) {
				craftingTimeLeft = Math.min(300, craftingTimeLeft++);
				return;
			}
			if (craftingTimeLeft > 0) craftingTimeLeft--;
			else {
				isCrafting = false;
				markDirty();

				ArrayList<ItemStack> stacks = new ArrayList<>();
				for (ClusterObject cluster : inventory) stacks.add(cluster.stack);
				SpellBuilder builder = new SpellBuilder(stacks);

				PacketHandler.NETWORK.sendToAllAround(new PacketExplode(new Vec3d(pos).addVector(0.5, 0.5, 0.5), Color.CYAN, Color.BLUE, 2, 2, 500, 300, 20, false),
						new NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 256));

				world.playSound(null, getPos(), ModSounds.BASS_BOOM, SoundCategory.BLOCKS, 1f, (float) RandUtil.nextDouble(1, 1.5));

				ItemStack stack = new ItemStack(ModItems.PEARL_NACRE);
				ItemNBTHelper.setFloat(stack, Constants.NBT.RAND, world.rand.nextFloat());
				ItemNBTHelper.setString(stack, "type", EnumPearlType.INFUSED.toString());

				NBTTagList list = new NBTTagList();
				for (Module module : builder.getSpell()) list.appendTag(module.serializeNBT());
				ItemNBTHelper.setList(stack, Constants.NBT.SPELL, list);

				output = stack;
				inventory.clear();
				craftingTimeLeft = craftingTime;
				markDirty();

				List<Entity> entityList = world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(pos).grow(32, 32, 32));
				for (Entity entity1 : entityList) {
					double dist = entity1.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
					final double upperMag = 3;
					final double scale = 0.8;
					double mag = upperMag * (scale * dist / (-scale * dist - 1) + 1);
					Vec3d dir = entity1.getPositionVector().subtract(new Vec3d(pos).addVector(0.5, 0.5, 0.5)).normalize().scale(mag);

					entity1.motionX = (dir.x);
					entity1.motionY = (dir.y);
					entity1.motionZ = (dir.z);
					entity1.fallDistance = 0;
					entity1.velocityChanged = true;

					if (entity1 instanceof EntityPlayerMP)
						((EntityPlayerMP) entity1).connection.sendPacket(new SPacketEntityVelocity(entity1));
				}
			}
		} else {
			if (craftingTimeLeft != craftingTime) {
				craftingTimeLeft = craftingTime;
				markDirty();
			}
		}
	}

	@Nonnull
	@SideOnly(Side.CLIENT)
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	public double getMaxRenderDistanceSquared() {
		return 100000;
	}
}
