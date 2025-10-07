package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.events.Render3DEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.settings.types.ColorSetting;
import star.sequoia2.settings.types.IntSetting;

public class TeleportIndicator extends ToggleFeature implements RenderUtilAccessor {

    ColorSetting color = settings().color("Color", "color of the sorrow", new mil.nga.color.Color(255, 255, 255));
    IntSetting range = settings().number("Range", "how far you will tp", 16, 1, 30);

    public TeleportIndicator() {
        super("TeleportIndicator", "Renders where you will teleport on mage and shaman");
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        double max = range.get();
        float tickDelta = event.delta();
        Vec3d start = mc.player.getCameraPosVec(tickDelta);
        Vec3d look = mc.player.getRotationVec(tickDelta);
        Vec3d end = start.add(look.multiply(max));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (hit.getPos().distanceTo(start) < 3) return;
            BlockPos candidate = hit.getBlockPos().offset(hit.getSide());
            BlockPos target = mc.world.isAir(candidate) ? candidate : hit.getBlockPos();
            render3DUtil().drawBox(event.matrices(), target, color.get(), 0.5);
        } else {
            BlockPos target = BlockPos.ofFloored(end);
            if (!mc.world.isAir(target)) return;
            render3DUtil().drawBox(event.matrices(), target, color.get(), 0.5);
        }
    }
}
