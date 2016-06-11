package me.lordsaad.wizardry.items;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Created by Saad on 6/10/2016.
 */
public class ItemPearl extends Item {

    public ItemPearl() {
        setRegistryName("pearl");
        setUnlocalizedName("pearl");
        GameRegistry.register(this);
        setMaxStackSize(1);
    }

    public static int intColor(int r, int g, int b) {
        return (r * 65536 + g * 256 + b);
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(getRegistryName(), "inventory"));
    }

    @Override
    public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
        if (!stack.hasTagCompound()) {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setInteger("red", 0);
            compound.setInteger("green", 0);
            compound.setInteger("blue", 0);
            compound.setString("type", "mundane");
            stack.setTagCompound(compound);
        }
        if (stack.hasTagCompound()) {
            if (stack.getTagCompound().hasKey("type")) {
                if (stack.getTagCompound().getString("type").equals("mundane")) {
                    for (int r = 0; r <= 255; r++)
                        for (int g = 0; g <= 255; g++)
                            for (int b = 0; b <= 255; b++) {
                                stack.getTagCompound().setInteger("red", r);
                                stack.getTagCompound().setInteger("green", g);
                                stack.getTagCompound().setInteger("blue", b);
                            }
                } else if (stack.getTagCompound().getString("type").equals("infused")) {

                } else {

                }
            }
        }
    }

    @Override
    public boolean canItemEditBlocks() {
        return false;
    }

    public static class ColorHandler implements IItemColor {
        public ColorHandler() {
        }

        @Override
        public int getColorFromItemstack(ItemStack stack, int tintIndex) {
            if (stack.hasTagCompound()) {
                if (tintIndex == 0) {
                    int r = stack.getTagCompound().getInteger("red");
                    int g = stack.getTagCompound().getInteger("green");
                    int b = stack.getTagCompound().getInteger("blue");
                    return intColor(r, g, b);
                }
            }
            return intColor(255, 255, 255);
        }
    }
}