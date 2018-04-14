package com.teamwizardry.wizardry.client.core;

import com.teamwizardry.wizardry.common.entity.EntityUnicorn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

/**
 * Created by Demoniaque.
 */
@SideOnly(Side.CLIENT)
public class UnicornTrailRenderer {

	public static UnicornTrailRenderer INSTANCE = new UnicornTrailRenderer();

	public HashMap<EntityUnicorn, List<Point>> positions = new HashMap<>();

	private UnicornTrailRenderer() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	private static BufferBuilder pos(BufferBuilder vb, Vec3d pos) {
		return vb.pos(pos.x, pos.y, pos.z);
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void tick(TickEvent.WorldTickEvent event) {
		World world = event.world;

		List<EntityUnicorn> unicorns = world.getEntities(EntityUnicorn.class, input -> true);

		for (EntityUnicorn unicorn : unicorns) {
			if (unicorn == null) continue;
			if (unicorn.isDead) {
				unicorns.remove(unicorn);
				break;
			}
			positions.putIfAbsent(unicorn, new ArrayList<>());

			List<Point> poses = positions.get(unicorn);

			Vec3d backCenter = unicorn.getPositionVector();
			Vec3d look = new Vec3d(unicorn.motionX, unicorn.motionY, unicorn.motionZ).normalize();
			backCenter = backCenter.add(look.scale(-1));

			if (poses.size() >= 1000) {
				poses.remove(0);
			}

			Vec3d cross = unicorn.getLook(0).crossProduct(new Vec3d(0, 1, 0)).scale(0.35f);
			poses.add(new Point(backCenter, cross, world.getTotalWorldTime()));
		}
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void render(RenderWorldLastEvent event) {
		World world = Minecraft.getMinecraft().world;
		EntityPlayer player = Minecraft.getMinecraft().player;
		if (player == null || world == null) return;

		GlStateManager.pushMatrix();

		double interpPosX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
		double interpPosY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
		double interpPosZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

		GlStateManager.translate(-interpPosX, -interpPosY + 0.1, -interpPosZ);

		GlStateManager.disableCull();
		//GlStateManager.enableAlpha();
		GlStateManager.enableBlend();
		GlStateManager.disableTexture2D();
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE);

		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder vb = tessellator.getBuffer();

		Set<EntityUnicorn> corns = new HashSet<>(positions.keySet());
		for (EntityUnicorn corn : corns) {
			if (corn.world.provider.getDimension() != world.provider.getDimension()) continue;

			List<Point> points = new ArrayList<>(positions.getOrDefault(corn, new ArrayList<>()));
			boolean q = false;

			vb.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
			for (Point pos : points) {
				if (pos == null) continue;

				float sub = (world.getTotalWorldTime() - pos.time);
				Color color = Color.getHSBColor(sub % 360.0f / 360.0f, 1f, 1f);

				int alpha;
				if (sub < 500) {
					alpha = (int) (MathHelper.clamp(Math.log(sub + 1) / 2.0, 0, 1) * 80.0);
				} else {
					alpha = (int) (MathHelper.clamp(1 - (Math.log(sub) / 2.0), 0, 1) * 80.0);
				}

				pos(vb, pos.origin.subtract(pos.normal)).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).endVertex();
				pos(vb, pos.origin.add(pos.normal)).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).endVertex();
				q = !q;
			}

			tessellator.draw();
		}

		GlStateManager.enableTexture2D();

		GlStateManager.popMatrix();
	}

	public class Point {

		@Nonnull
		public final Vec3d origin;
		@Nonnull
		public final Vec3d normal;
		private final long time;

		public Point(@Nonnull Vec3d origin, @Nonnull Vec3d normal, long time) {
			this.origin = origin;
			this.normal = normal;
			this.time = time;
		}
	}
}
