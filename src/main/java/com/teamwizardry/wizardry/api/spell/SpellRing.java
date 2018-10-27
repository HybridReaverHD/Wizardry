package com.teamwizardry.wizardry.api.spell;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ArrayListMultimap;
import com.teamwizardry.wizardry.Wizardry;
import com.teamwizardry.wizardry.api.capability.mana.CapManager;
import com.teamwizardry.wizardry.api.item.BaublesSupport;
import com.teamwizardry.wizardry.api.spell.attribute.AttributeModifier;
import com.teamwizardry.wizardry.api.spell.attribute.AttributeRange;
import com.teamwizardry.wizardry.api.spell.attribute.AttributeRegistry;
import com.teamwizardry.wizardry.api.spell.attribute.AttributeRegistry.Attribute;
import com.teamwizardry.wizardry.api.spell.attribute.Operation;
import com.teamwizardry.wizardry.api.spell.module.ModuleInstance;
import com.teamwizardry.wizardry.api.spell.module.ModuleInstanceEffect;
import com.teamwizardry.wizardry.api.spell.module.ModuleInstanceModifier;
import com.teamwizardry.wizardry.api.util.FixedPointUtils;
import com.teamwizardry.wizardry.init.ModItems;
import com.teamwizardry.wizardry.init.ModSounds;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Modules ala IBlockStates
 */
public class SpellRing implements INBTSerializable<NBTTagCompound> {

	/**
	 * Mostly used as a cache key. <br/>
	 * <b>NOTE</b>: Must be initialized only by serializeNBT() to have a normalized key!
	 */
	private NBTTagCompound serializedTag = null;
	
	/**
	 * Store all processed modifier info and any extra you want here.
	 * Used by modifier processing and the WorktableGUI to save GUI in TileWorktable
	 */
	private NBTTagCompound informationTag = new NBTTagCompound();
	
	/**
	 * A map holding compile time modifiers.
	 */
	@Nonnull
	private ArrayListMultimap<Operation, AttributeModifierSpellRing> compileTimeModifiers = ArrayListMultimap.create();
	
	/**
	 * Primary rendering color.
	 */
	@Nonnull
	private Color primaryColor = Color.WHITE;

	/**
	 * Secondary rendering color.
	 */
	@Nonnull
	private Color secondaryColor = Color.WHITE;

	/**
	 * The Module of this Ring.
	 */
	@Nullable
	private ModuleInstance module;

	/**
	 * The parent ring of this Ring, the ring that will have been run before this.
	 */
	@Nullable
	private SpellRing parentRing = null;

	/**
	 * The child ring of this Ring, the ring that will run after this.
	 */
	@Nullable
	private SpellRing childRing = null;

	private SpellRing() {
	}

	public SpellRing(@Nonnull ModuleInstance module) {
		setModule(module);
	}

	static SpellRing deserializeRing(NBTTagCompound compound) {
		SpellRing ring = new SpellRing();
		ring.deserializeNBT(compound);

		SpellRing lastRing = ring;
		while (lastRing != null) {
			if (lastRing.getChildRing() == null) break;

			lastRing = lastRing.getChildRing();
		}
		if (lastRing != null) lastRing.updateColorChain();

		return ring;
	}

	/**
	 * Will run the spellData from this ring and down to it's children including rendering.
	 *
	 * @param data The SpellData object.
	 */
	public boolean runSpellRing(SpellData data) {
		if (module == null) return false;

		if (data.getCaster() != null)
			data.processCastTimeModifiers(data.getCaster(), this);
		boolean success = module.castSpell(data, this) && !module.ignoreResultForRendering();
		if (success) {

			if (module != null) {
				module.sendRenderPacket(data, this);
			}

			if (getChildRing() != null) return getChildRing().runSpellRing(data);
		} else if (module.ignoreResultForRendering()) {
			if (module != null) {
				module.sendRenderPacket(data, this);
			}
		}

		return success;
	}

	public boolean isContinuous() {
		return module.getModuleClass() instanceof IContinuousModule;
	}

	public Set<SpellRing> getOverridingRings() {
		Set<SpellRing> set = new HashSet<>();
		if (module == null) return set;

		for (SpellRing child : getAllChildRings()) {
			if (child.getModule() == null) continue;
			if (isRunBeingOverridenBy(child.getModule())) set.add(child);
		}

		return set;
	}

	public boolean isRunBeingOverriden() {
		if (module == null) return false;

		for (SpellRing child : getAllChildRings()) {
			if (child.getModule() == null) continue;
			if (isRunBeingOverridenBy(child.getModule())) return true;
		}

		return false;
	}

	@SideOnly(Side.CLIENT)
	public boolean isRenderBeingOverriden() {
		if (module == null) return false;

		for (SpellRing child : getAllChildRings()) {
			if (child.getModule() == null) continue;
			if (isRenderBeingOverridenBy(child.getModule())) return true;
		}

		return false;
	}

	/**
	 * If the given module is overriding this module's run
	 */
	public boolean isRunBeingOverridenBy(@Nonnull ModuleInstance module) {
		return this.module != null && module instanceof ModuleInstanceEffect && ((ModuleInstanceEffect) module).hasRunOverrideFor(this.module);
	}

	/**
	 * If the given module is overriding this module's run
	 */
	@SideOnly(Side.CLIENT)
	public boolean isRenderBeingOverridenBy(@Nonnull ModuleInstance module) {
		return this.module != null && module instanceof ModuleInstanceEffect && ((ModuleInstanceEffect) module).hasRenderOverrideFor(this.module);
	}

	//TODO: pearl holders
	public boolean taxCaster(SpellData data, double multiplier, boolean failSound) {
		Entity caster = data.getCaster();
		if (caster == null) return false;

		double manaDrain = getManaDrain(data) * multiplier;
		double burnoutFill = getBurnoutFill(data) * multiplier;

		boolean fail = false;

		try (CapManager.CapManagerBuilder mgr = CapManager.forObject(caster)) {
			if (mgr.getMana() < manaDrain) fail = true;

			mgr.removeMana(manaDrain);
			mgr.addBurnout(burnoutFill);
		}

		if (fail && failSound) {
			World world = data.world;
			Vec3d origin = data.getOriginWithFallback();
			if (origin != null)
				world.playSound(null, new BlockPos(origin), ModSounds.SPELL_FAIL, SoundCategory.NEUTRAL, 1f, 1f);
		}

		return !fail;
	}

	public boolean taxCaster(SpellData data, boolean failSound) {
		return taxCaster(data, 1, failSound);
	}

	/**
	 * Get a modifier in this ring between the range. Returns the attribute value, modified by burnout and multipliers, for use in a spell.
	 *
	 * @param attribute The attribute you want. List in {@link AttributeRegistry} for default attributes.
	 * @param data      The data of the spell being cast, used to get caster-specific modifiers.
	 * @return The {@code double} potency of a modifier.
	 */
	public final double getAttributeValue(Attribute attribute, SpellData data) {
		if (module == null) return 0;

		double current = getDoubleFromNBT(informationTag, attribute.getNbtName());

		AttributeRange range = module.getAttributeRanges().get(attribute);

		current = MathHelper.clamp(current, range.min, range.max);
		current = data.getCastTimeValue(attribute, current);
		current *= getPlayerBurnoutMultiplier(data);
		current *= getPowerMultiplier();
		
		return current;
	}

	/**
	 * Get a modifier in this ring between the range. Returns the true attribute value, unmodified by any other attributes.
	 *
	 * @param attribute The attribute you want. List in {@link AttributeRegistry} for default attributes.
	 * @return The {@code double} potency of a modifier.
	 */
	public final double getTrueAttributeValue(Attribute attribute) {
		if (module == null) return 0;

		double current = getDoubleFromNBT(informationTag, attribute.getNbtName());

		AttributeRange range = module.getAttributeRanges().get(attribute);

		return MathHelper.clamp(current, range.min, range.max);
	}
	
	/**
	 * Will process all modifiers and attributes set.
	 * WILL RESET THE INFORMATION TAG.
	 */
	public void processModifiers() {
		informationTag = new NBTTagCompound();

		if (module != null) {
			module.getAttributeRanges().forEach((attribute, range) -> {
				setDoubleToNBT(informationTag, attribute.getNbtName(), range.base);
			});
		}

		for (Operation op : Operation.values()) {
			for (AttributeModifier modifier : compileTimeModifiers.get(op)) {

				if (!informationTag.hasKey(modifier.getAttribute().getNbtName()))
					continue;
				double current = getDoubleFromNBT(informationTag, modifier.getAttribute().getNbtName());
				double newValue = modifier.apply(current);

				setDoubleToNBT(informationTag, modifier.getAttribute().getNbtName(), newValue);

				Wizardry.logger.info(module == null ? "<null module>" : module.getSubModuleID() + ": Attribute: " + modifier.getAttribute() + ": " + current + "-> " + newValue);
			}
		}
	}
	
//	public final float getCapeReduction(EntityLivingBase caster) {
//		ItemStack stack = BaublesSupport.getItem(caster, ModItems.CAPE);
//		if (stack != ItemStack.EMPTY) {
//			float time = ItemNBTHelper.getInt(stack, "maxTick", 0);
//			return (float) MathHelper.clamp(1 - (time / 1000000.0), 0.25, 1);
//		}
//		return 1;
//	}

	/**
	 * Get all the children rings of this ring excluding itself.
	 */
	public final Set<SpellRing> getAllChildRings() {
		Set<SpellRing> childRings = new HashSet<>();

		if (childRing == null) return childRings;

		SpellRing tempModule = childRing;
		while (tempModule != null) {
			childRings.add(tempModule);
			tempModule = tempModule.getChildRing();
		}
		return childRings;
	}

	@Nullable
	public SpellRing getChildRing() {
		return childRing;
	}

	public void setChildRing(@Nonnull SpellRing childRing) {
		this.childRing = childRing;
	}

	@Nullable
	public SpellRing getParentRing() {
		return parentRing;
	}

	public void setParentRing(@Nullable SpellRing parentRing) {
		this.parentRing = parentRing;
	}

	@Nullable
	public ModuleInstance getModule() {
		return module;
	}

	public void setModule(@Nonnull ModuleInstance module) {
		this.module = module;

		setPrimaryColor(module.getPrimaryColor());
		setSecondaryColor(module.getSecondaryColor());
	}

	@Nonnull
	public Color getPrimaryColor() {
		return primaryColor;
	}

	public void setPrimaryColor(@Nonnull Color primaryColor) {
		this.primaryColor = primaryColor;
		updateColorChain();
	}

	@Nonnull
	public Color getSecondaryColor() {
		return secondaryColor;
	}

	public void setSecondaryColor(@Nonnull Color secondaryColor) {
		this.secondaryColor = secondaryColor;
	}

	public void updateColorChain() {
		if (getParentRing() == null) return;

		getParentRing().setPrimaryColor(getPrimaryColor());
		getParentRing().setSecondaryColor(getSecondaryColor());
		getParentRing().updateColorChain();
	}

	public double getPowerMultiplier() {
		return getTrueAttributeValue(AttributeRegistry.POWER_MULTI);
	}

	public double getManaMultiplier() {
		return getTrueAttributeValue(AttributeRegistry.MANA_MULTI);
	}

	public double getBurnoutMultiplier() {
		return getTrueAttributeValue(AttributeRegistry.BURNOUT_MULTI);
	}

	/**
	 * Returns mana drain value. If spell data is passed, then the value is modified additionally by runtime data,
	 * e.g. by cape and halo attributes of caster.
	 * 
	 * @param data runtime data of active spell. Can be <code>null</code>.
	 * @return mana drain value
	 */
	public double getManaDrain(SpellData data) {
		double value = getDoubleFromNBT(informationTag, AttributeRegistry.MANA.getNbtName());
		if( data != null )
			value = data.getCastTimeValue(AttributeRegistry.MANA, value);
		return value * getManaMultiplier();
	}

	/**
	 * Returns burnout fill value. If spell data is passed, then the value is modified additionally by runtime data,
	 * e.g. by cape and halo attributes of caster.
	 * 
	 * @param data runtime data of active spell. Can be <code>null</code>.
	 * @return burnout fill value
	 */
	public double getBurnoutFill(SpellData data) {
		double value = getDoubleFromNBT(informationTag, AttributeRegistry.BURNOUT.getNbtName());
		if( data != null )
			value = data.getCastTimeValue(AttributeRegistry.BURNOUT, value); 
		return value * getBurnoutMultiplier();
	}

// Avatair: Don't know what to do with it ...
//	@Nonnull
//	public ArrayListMultimap<Operation, AttributeModifier> getModifiers() {
//		return (ArrayListMultimap<Operation, AttributeModifier>)compileTimeModifiers;
//	}

	public void addModifier(ModuleInstanceModifier moduleModifier) {
		moduleModifier.getAttributes().forEach(modifier -> compileTimeModifiers.put(modifier.getOperation(), new AttributeModifierSpellRing(modifier)));
	}

	public void addModifier(AttributeModifier attributeModifier) {
		compileTimeModifiers.put(attributeModifier.getOperation(), new AttributeModifierSpellRing(attributeModifier));
	}

	public int getCooldownTime(@Nullable SpellData data) {
		if (data != null && module.getModuleClass() instanceof IOverrideCooldown)
			return ((IOverrideCooldown) module.getModuleClass()).getNewCooldown(data, this);

		return (int) getDoubleFromNBT(informationTag, AttributeRegistry.COOLDOWN.getNbtName());
	}

	public int getCooldownTime() {
		return getCooldownTime(null);
	}

	public int getChargeUpTime() {
		return (int) getDoubleFromNBT(informationTag, AttributeRegistry.CHARGEUP.getNbtName());
	}

	/**
	 * All non mana, burnout, and multiplier attributes are reduced based on the caster's burnout level. This returns how much to reduce them by.
	 *
	 * @return The INVERTED burnout multiplier.
	 */
	public double getPlayerBurnoutMultiplier(SpellData data) {
		Entity caster = data.getCaster();
		if (caster == null || caster instanceof EntityLivingBase && BaublesSupport.getItem((EntityLivingBase) caster, ModItems.CREATIVE_HALO, ModItems.FAKE_HALO, ModItems.REAL_HALO).isEmpty())
			return 1;

		double multiplier = CapManager.getBurnout(caster) / CapManager.getMaxBurnout(caster);
		double burnoutLimit = 0.5; //TODO: Probably put this into config, limit to [0, 1)
		return Math.min(1, 1 - (multiplier - burnoutLimit) / (1 - burnoutLimit));
	}

	@Nullable
	public String getModuleReadableName() {
		return module != null ? module.getReadableName() : null;
	}

	public NBTTagCompound getInformationTag() {
		return informationTag;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();

		SpellRing ring = this;
		while (ring != null) {
			builder.append(ring.getModuleReadableName()).append(ring.getChildRing() == null ? "" : " > ");
			ring = ring.getChildRing();
		}

		return builder.toString();
	}

	@Override
	public NBTTagCompound serializeNBT() {
		if( serializedTag == null ) {
			serializedTag = internalSerializeNBT();
		}
		return serializedTag;
	}
	
	private NBTTagCompound internalSerializeNBT() {
		NBTTagCompound compound = new NBTTagCompound();

		if (!compileTimeModifiers.isEmpty()) {
			NBTTagList attribs = new NBTTagList();
			compileTimeModifiers.forEach((op, modifier) -> {
				NBTTagCompound modifierCompound = new NBTTagCompound();

				modifierCompound.setInteger("operation", modifier.getOperation().ordinal());
				modifierCompound.setString("attribute", modifier.getAttribute().getNbtName());
				setFixedToNBT(modifierCompound, "modifier", modifier.getModifierFixed());
				attribs.appendTag(modifierCompound);
			});
			compound.setTag("modifiers", attribs);
		}

		compound.setTag("extra", informationTag);
		compound.setString("primary_color", String.valueOf(primaryColor.getRGB()));
		compound.setString("secondary_color", String.valueOf(secondaryColor.getRGB()));

		if (childRing != null) compound.setTag("child_ring", this.childRing.serializeNBT());
		if (module != null) compound.setString("module", module.getSubModuleID());

		return compound;
	}

	@Override
	public void deserializeNBT(NBTTagCompound nbt) {
		// NOTE: Don't store nbt to serializedNBT. This one must be generated only by serializeNBT()
		
		if (nbt.hasKey("module")) this.module = ModuleInstance.deserialize(nbt.getString("module"));
		if (nbt.hasKey("extra")) informationTag = nbt.getCompoundTag("extra");
		if (nbt.hasKey("primary_color")) primaryColor = Color.decode(nbt.getString("primary_color"));
		if (nbt.hasKey("secondary_color")) secondaryColor = Color.decode(nbt.getString("secondary_color"));

		if (nbt.hasKey("modifiers")) {
			compileTimeModifiers.clear();
			for (NBTBase base : nbt.getTagList("modifiers", Constants.NBT.TAG_COMPOUND)) {
				if (base instanceof NBTTagCompound) {
					NBTTagCompound modifierCompound = (NBTTagCompound) base;
					if (modifierCompound.hasKey("operation") && modifierCompound.hasKey("attribute") && modifierCompound.hasKey("modifier")) {
						Operation operation = Operation.values()[modifierCompound.getInteger("operation") % Operation.values().length];
						Attribute attribute = AttributeRegistry.getAttributeFromName(modifierCompound.getString("attribute"));

						long modifierFixed = getFixedFromNBT(modifierCompound, "modifier");
						compileTimeModifiers.put(operation, new AttributeModifierSpellRing(attribute, modifierFixed, operation));
						
/*						byte dataType = modifierCompound.getTagId("modifier"); // See net.minecraft.nbt.NBTBase.NBT_TYPES[] for meaning of type ids.
						if( dataType == 5 || dataType == 6 ) {
							// NOTE: For legacy case only.
							
							// If 5=float or 6=double type
							double modifier = modifierCompound.getDouble("modifier");
							compileTimeModifiers.put(operation, new AttributeModifierSpellRing(attribute, modifier, operation));
						}
						else if( dataType == 1 || dataType == 2 || dataType == 3 || dataType == 4 ) {
							// If 1=byte, 2=short, 3=int, 4=long
							long modifierFixed = modifierCompound.getLong("modifier");
							compileTimeModifiers.put(operation, new AttributeModifierSpellRing(attribute, modifierFixed, operation));
						}
						else {
							// Ignore.
						}*/
					}
				}
			}
		}

		if (nbt.hasKey("child_ring")) {
			SpellRing childRing = deserializeRing(nbt.getCompoundTag("child_ring"));
			childRing.setParentRing(this);
			setChildRing(childRing);
		}
	}

//	public SpellRing copy() {
//		return deserializeRing(serializeNBT());
//	}
	
	////////////////////
	
	private double getDoubleFromNBT(NBTTagCompound nbt, String key) {
		// See net.minecraft.nbt.NBTBase.NBT_TYPES[] for meaning of type ids.
		
		byte dataType = nbt.getTagId(key);
		if( dataType == 5 || dataType == 6 )
			return nbt.getDouble(key);	// NOTE: For legacy case
		else if( dataType == 1 || dataType == 2 || dataType == 3 || dataType == 4 )
			return FixedPointUtils.fixedToDouble(nbt.getLong(key));
		return 0;
	}
	
	private long getFixedFromNBT(NBTTagCompound nbt, String key) {
		// See net.minecraft.nbt.NBTBase.NBT_TYPES[] for meaning of type ids.
		
		byte dataType = nbt.getTagId(key);
		if( dataType == 5 || dataType == 6 )
			return FixedPointUtils.doubleToFixed(nbt.getDouble(key));	// NOTE: For legacy case
		else if( dataType == 1 || dataType == 2 || dataType == 3 || dataType == 4 )
			return nbt.getLong(key);
		return 0;		
	}
	
	private void setDoubleToNBT(NBTTagCompound nbt, String key, double value) {
		nbt.setLong(key, FixedPointUtils.doubleToFixed(value));
	}
	
	private void setFixedToNBT(NBTTagCompound nbt, String key, long value) {
		nbt.setLong(key, value);
	}
	
	////////////////////
	
	/**
	 * Storage class for attribute modifiers. Helps to avoid using double values in NBT.
	 * As the {@link #equals(Object)} method isn't reliable for them.
	 * 
	 * @author Avatair
	 */
	private static class AttributeModifierSpellRing extends AttributeModifier {
		
		private long modifierFixed;
		
		public AttributeModifierSpellRing(AttributeModifier modifier) {
			this(modifier.getAttribute(), modifier.getModifier(), modifier.getOperation());
		}

		public AttributeModifierSpellRing(Attribute attribute, double modifier, Operation op) {
			super(attribute, modifier, op);
			this.modifierFixed = FixedPointUtils.doubleToFixed(modifier);
		}
		
		public AttributeModifierSpellRing(Attribute attribute, long modifierFixed, Operation op) {
			super(attribute, FixedPointUtils.fixedToDouble(modifierFixed), op);
		}

		public long getModifierFixed() {
			return this.modifierFixed;
		}

		public void setModifier(double newValue) {
			this.modifierFixed = FixedPointUtils.doubleToFixed(newValue);
			super.setModifier(newValue);
		}

		public void setModifierFixed(long newValueFixed) {
			this.modifierFixed = newValueFixed;
		}
		
		@Override
		public AttributeModifier copy() {
			return new AttributeModifierSpellRing(getAttribute(), modifierFixed, getOperation());
		}
	}
}
