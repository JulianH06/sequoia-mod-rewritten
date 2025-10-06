package star.sequoia2.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wynntils.core.components.Handlers;
import com.wynntils.core.components.Models;
import com.wynntils.core.components.Services;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.services.map.MapTexture;
import com.wynntils.services.map.pois.Poi;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.mc.KeyboardUtils;
import com.wynntils.utils.render.MapRenderer;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.type.BoundingBox;
import com.wynntils.utils.type.BoundingShape;
import com.wynntils.utils.type.CappedValue;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.utils.render.TextureStorage;

import java.util.*;
import java.util.stream.Collectors;

import static com.wynntils.models.territories.type.GuildResource.*;
import static star.sequoia2.client.SeqClient.mc;

public class BetterGuildMapScreen extends Screen implements RenderUtilAccessor, TextRendererAccessor {
    enum Roles { TANK, DPS, HEAL }
    enum UiState { OPTED_OUT, CHOOSING_ROLE, CHOSEN_ROLE }
    enum Command { ATTACK, MOVE, SWITCH_CLASS }

    static class Rect {
        final float x, y, w, h;
        Rect(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean contains(double mx, double my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    }

    static class TexturePair {
        final Identifier active, inactive;
        TexturePair(Identifier active, Identifier inactive) { this.active = active; this.inactive = inactive; }
    }

    static class Warrer {
        final UUID id;
        final String name;
        Roles role;
        String group;
        double x, z;
        boolean selected;
        final Deque<String> queue = new ArrayDeque<>();
        Warrer(UUID id, String name, Roles role, String group) { this.id=id; this.name=name; this.role=role; this.group=group; }
    }

    CappedValue ore, fish, wood, crop;

    static final float SCALE = 1f, PAD = 6f * SCALE;
    static final float BTN_SIZE = 46f * SCALE, BTN_GAP = 8f * SCALE, BAR_HEIGHT = BTN_SIZE + PAD * 2f, BAR_CORNER = 6f * SCALE;
    static final float OPT_BTN_SIZE = 42f * SCALE, OPT_CORNER = 5f * SCALE, OPT_OUT_SIZE = 20f * SCALE;
    static final float CMD_BTN_SIZE = 42f * SCALE, CMD_CORNER = 5f * SCALE;

    static final float RTS_BAR_H = 88f, RTS_HEAD = 56f, RTS_HEAD_PAD = 10f, RTS_GROUP_TAB_H = 22f, RTS_GROUP_TAB_PAD = 6f;

    static final float CMD_BOX_W = 160f, CMD_BOX_H = 64f, CMD_BOX_CORNER = 8f;

    static final VertexConsumerProvider.Immediate BUFFER_SOURCE = VertexConsumerProvider.immediate(new BufferAllocator(256));

    float renderWidth, renderHeight, renderX, renderY, renderedBorderXOffset, renderedBorderYOffset, mapWidth, mapHeight, centerX, centerZ, mapCenterX, mapCenterZ;
    float zoomLevel = 60f, zoomRenderScale = MapRenderer.getZoomRenderScaleFromLevel(60f);
    Poi hovered;

    final EnumMap<Roles, TexturePair> roleTextures = new EnumMap<>(Roles.class);
    UiState state = UiState.OPTED_OUT;
    Roles selectedRole = null;

    final Map<String, List<Warrer>> groups = new LinkedHashMap<>();
    String currentGroup = "All";
    String myGroup = "Group A";
    int warrerScroll = 0;
    Command currentCommand = Command.MOVE;
    Roles switchRequestRole = Roles.TANK;

    boolean optedIn = false;

    public BetterGuildMapScreen() {
        super(Text.literal("Map"));
        roleTextures.put(Roles.TANK, new TexturePair(TextureStorage.tank_active, TextureStorage.tank_inactive));
        roleTextures.put(Roles.DPS, new TexturePair(TextureStorage.damage_active, TextureStorage.damage_inactive));
        roleTextures.put(Roles.HEAL, new TexturePair(TextureStorage.healer_active, TextureStorage.healer_inactive));
        groups.put("All", new ArrayList<>());
        groups.put("Group A", new ArrayList<>());
        groups.put("Group B", new ArrayList<>());
    }

    @Override
    protected void init() {
        renderWidth = this.width;
        renderHeight = this.height;
        renderX = 0f;
        renderY = 0f;
        renderedBorderXOffset = 0f;
        renderedBorderYOffset = 0f;
        mapWidth = renderWidth;
        mapHeight = renderHeight;
        centerX = renderX + renderedBorderXOffset + mapWidth / 2f;
        centerZ = renderY + renderedBorderYOffset + mapHeight / 2f;
        if (mc.player != null) {
            mapCenterX = (float) mc.player.getX();
            mapCenterZ = (float) mc.player.getZ();
        } else {
            mapCenterX = -360f;
            mapCenterZ = -3000f;
        }
        zoomRenderScale = MapRenderer.getZoomRenderScaleFromLevel(zoomLevel);
        hovered = null;
        seedDemoWarrers();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
        renderMap(context);

        render2DUtil().enableScissor((int)(renderX + renderedBorderXOffset), (int)(renderY + renderedBorderYOffset), (int)(renderX + renderedBorderXOffset + mapWidth), (int)(renderY + renderedBorderYOffset + mapHeight - (optedIn ? RTS_BAR_H : 0)));
        renderPois(context.getMatrices(), mouseX, mouseY);
        if (hasSelectedFromMyGroup()) renderRtsDottedLines(context.getMatrices());
        render2DUtil().disableScissor();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        MatrixStack m = context.getMatrices();
        m.push();
        m.translate(0, 0, 300);

        drawRoleUI(context, mouseX, mouseY);
        drawOptInToggle(context, mouseX, mouseY);
        drawHqResourceUI(context, mouseX, mouseY);
        if (optedIn) drawRtsBar(context, mouseX, mouseY);
        if (hasSelectedFromMyGroup()) drawCommandBox(context, mouseX, mouseY);

        m.pop();
        RenderSystem.enableDepthTest();
    }

    void fillRoundedGradient(DrawContext ctx, Rect r, float radius, Color start, Color end, boolean sideways) {
        render2DUtil().roundGradientFilled(ctx.getMatrices(), r.x, r.y, r.x + r.w, r.y + r.h, radius, start, end, sideways);
    }

    void drawOptInToggle(DrawContext ctx, int mouseX, int mouseY) {
        float baseX = renderX + renderedBorderXOffset + mapWidth / 2f;
        float baseY = renderY + renderedBorderYOffset + PAD;
        Rect r = new Rect(baseX - CMD_BTN_SIZE / 2f, baseY, CMD_BTN_SIZE, CMD_BTN_SIZE);
        boolean hover = r.contains(mouseX, mouseY);
        Color s = optedIn ? new Color(70, 110, 255, 220) : new Color(48, 48, 54, 220);
        Color e = optedIn ? new Color(40, 70, 220, 220)  : new Color(26, 26, 32, 220);
        if (hover) {
            s = new Color(Math.min(255, s.getRed() + 20), Math.min(255, s.getGreen() + 20), Math.min(255, s.getBlue() + 20), s.getAlpha());
            e = new Color(Math.min(255, e.getRed() + 20), Math.min(255, e.getGreen() + 20), Math.min(255, e.getBlue() + 20), e.getAlpha());
            render2DUtil().drawGlow(ctx, r.x, r.y, r.x + r.w, r.y + r.h, new Color(90, 120, 255, 140), CMD_CORNER);
        }
        fillRoundedGradient(ctx, r, CMD_CORNER, s, e, true);
        Identifier tex = optedIn ? TextureStorage.command_active : TextureStorage.command_inactive;
        render2DUtil().drawTexture(ctx, tex, r.x + 4, r.y + 4, r.x + r.w - 4, r.y + r.h - 4);
    }

    void drawCommandBox(DrawContext ctx, int mouseX, int mouseY) {
        float x2 = renderX + renderedBorderXOffset + mapWidth - PAD;
        float y2 = renderY + renderedBorderYOffset + mapHeight - PAD;
        Rect panel = new Rect(x2 - CMD_BOX_W, y2 - CMD_BOX_H - (optedIn ? RTS_BAR_H + PAD : 0), CMD_BOX_W, CMD_BOX_H);
        Color s = new Color(56,56,64,230);
        Color e = new Color(38,38,46,230);
        fillRoundedGradient(ctx, panel, CMD_BOX_CORNER, s, e, true);

        float bx = panel.x + 10;
        float by = panel.y + (panel.h - CMD_BTN_SIZE) / 2f;
        Rect move = new Rect(bx, by, CMD_BTN_SIZE, CMD_BTN_SIZE);
        Rect attack = new Rect(bx + CMD_BTN_SIZE + 8, by, CMD_BTN_SIZE, CMD_BTN_SIZE);
        Rect sw = new Rect(bx + (CMD_BTN_SIZE + 8) * 2, by, CMD_BTN_SIZE, CMD_BTN_SIZE);

        Identifier mt = TextureStorage.healer_active; //move
        Identifier at = TextureStorage.damage_active; //attack
        Identifier wt = TextureStorage.tank_active; //switch
        render2DUtil().drawTexture(ctx, mt, move.x, move.y, move.x + move.w, move.y + move.h);
        render2DUtil().drawTexture(ctx, at, attack.x, attack.y, attack.x + attack.w, attack.y + attack.h);
        render2DUtil().drawTexture(ctx, wt, sw.x, sw.y, sw.x + sw.w, sw.y + sw.h);

        String swRole = switchRequestRole.name();
        render2DUtil().drawText(ctx, swRole, (int)(sw.x + sw.w/2f - textRenderer().getWidth(swRole)/2f), (int)(sw.y + sw.h + 2), 0xFFFFFF, true);
    }

    void drawHqResourceUI(DrawContext ctx, int mouseX, int mouseY) {
        float baseX = renderX + renderedBorderXOffset + mapWidth - PAD;
        float baseY = renderY + renderedBorderYOffset + PAD;

        float iconH = 20f * SCALE;
        float defaultIconW = iconH * 2.5f;
        float gap = 6f * SCALE;

        int lh = textRenderer().fontHeight;
        float namePad = lh + 2f;
        float valuePad = lh;

        String oreVal = valueText(ore);
        String fishVal = valueText(fish);
        String woodVal = valueText(wood);
        String cropVal = valueText(crop);

        int maxTextW = Math.max(
                Math.max(textRenderer().getWidth("Ore"), textRenderer().getWidth("Wood")),
                Math.max(textRenderer().getWidth("Fish"), textRenderer().getWidth("Crops")));
        maxTextW = Math.max(maxTextW, Math.max(
                Math.max(textRenderer().getWidth(oreVal), textRenderer().getWidth(woodVal)),
                Math.max(textRenderer().getWidth(fishVal), textRenderer().getWidth(cropVal))));

        float iconW = Math.max(defaultIconW, maxTextW);

        float panelW = PAD + iconW + gap + iconW + PAD;
        float panelH = PAD + namePad + valuePad + iconH + gap + namePad + valuePad + iconH + PAD;

        Rect panel = new Rect(baseX - panelW, baseY, panelW, panelH);
        boolean hover = panel.contains(mouseX, mouseY);
        Color s = new Color(40, 40, 46, 220);
        Color e = new Color(22, 22, 28, 220);
        if (hover) {
            s = new Color(50, 50, 58, 230);
            e = new Color(28, 28, 36, 230);
            render2DUtil().drawGlow(ctx, panel.x, panel.y, panel.x + panel.w, panel.y + panel.h, new Color(80, 110, 255, 90), 6f);
        }
        fillRoundedGradient(ctx, panel, 5f, s, e, false);

        float x1 = panel.x + PAD;
        float x2 = x1 + iconW + gap;
        float y1 = panel.y + PAD + namePad + valuePad;
        float y2 = y1 + iconH + gap + namePad + valuePad;

        render2DUtil().drawText(ctx, "Ore",  (int)(x1 + iconW / 2f - textRenderer().getWidth("Ore")  / 2f),  (int)(y1 - valuePad - lh - 2), 0xFFFFFF, true);
        render2DUtil().drawText(ctx, oreVal, (int)(x1 + iconW / 2f - textRenderer().getWidth(oreVal) / 2f), (int)(y1 - lh - 2), 0xFFFFFF, true);
        drawResourceMeter(ctx, x1, y1, iconW, iconH, valueRatio(ore), TextureStorage.ore_empty, TextureStorage.ore_full);

        render2DUtil().drawText(ctx, "Fish", (int)(x2 + iconW / 2f - textRenderer().getWidth("Fish") / 2f), (int)(y1 - valuePad - lh - 2), 0xFFFFFF, true);
        render2DUtil().drawText(ctx, fishVal,(int)(x2 + iconW / 2f - textRenderer().getWidth(fishVal)/ 2f), (int)(y1 - lh - 2), 0xFFFFFF, true);
        drawResourceMeter(ctx, x2, y1, iconW, iconH, valueRatio(fish), TextureStorage.fish_empty, TextureStorage.fish_full);

        render2DUtil().drawText(ctx, "Wood", (int)(x1 + iconW / 2f - textRenderer().getWidth("Wood") / 2f), (int)(y2 - valuePad - lh - 2), 0xFFFFFF, true);
        render2DUtil().drawText(ctx, woodVal,(int)(x1 + iconW / 2f - textRenderer().getWidth(woodVal)/ 2f), (int)(y2 - lh - 2), 0xFFFFFF, true);
        drawResourceMeter(ctx, x1, y2, iconW, iconH, valueRatio(wood), TextureStorage.wood_empty, TextureStorage.wood_full);

        render2DUtil().drawText(ctx, "Crops",(int)(x2 + iconW / 2f - textRenderer().getWidth("Crops")/ 2f), (int)(y2 - valuePad - lh - 2), 0xFFFFFF, true);
        render2DUtil().drawText(ctx, cropVal,(int)(x2 + iconW / 2f - textRenderer().getWidth(cropVal)/ 2f), (int)(y2 - lh - 2), 0xFFFFFF, true);
        drawResourceMeter(ctx, x2, y2, iconW, iconH, valueRatio(crop), TextureStorage.crop_empty, TextureStorage.crop_full);
    }

    void drawResourceMeter(DrawContext ctx, float x, float y, float w, float h, double ratio, Identifier emptyTex, Identifier fullTex) {
        render2DUtil().drawTexture(ctx, emptyTex, x, y, x + w, y + h);
        int sx = (int) x;
        int sy = (int) y;
        int sw = (int) (w * Math.max(0d, Math.min(1d, ratio)));
        int sh = (int) h;
        if (sw > 0 && sh > 0) {
            render2DUtil().enableScissor(sx, sy, sx + sw, sy + sh);
            render2DUtil().drawTexture(ctx, fullTex, x, y, x + w, y + h);
            render2DUtil().disableScissor();
        }
    }

    void drawRoleUI(DrawContext ctx, int mouseX, int mouseY) {
        float baseX = renderX + renderedBorderXOffset;
        float baseY = renderY + renderedBorderYOffset;

        if (state == UiState.OPTED_OUT) {
            Rect r = new Rect(baseX + PAD, baseY + PAD, OPT_BTN_SIZE, OPT_BTN_SIZE);
            boolean hover = r.contains(mouseX, mouseY);
            Color s = new Color(46, 46, 52, 220);
            Color e = new Color(28, 28, 34, 220);
            if (hover) {
                s = new Color(56, 56, 64, 230);
                e = new Color(34, 34, 42, 230);
                render2DUtil().drawGlow(ctx, r.x, r.y, r.x + r.w, r.y + r.h, new Color(80, 110, 255, 90), OPT_CORNER);
            }
            fillRoundedGradient(ctx, r, OPT_CORNER, s, e, true);
            render2DUtil().drawTexture(ctx, TextureStorage.opt_in, r.x + 4, r.y + 4, r.x + r.w - 4, r.y + r.h - 4);
            return;
        }

        float barWidth = PAD + (BTN_SIZE * 3f) + (BTN_GAP * 2f) + PAD;
        Rect bar = new Rect(baseX, baseY + PAD, barWidth, BAR_HEIGHT);
        boolean barHover = bar.contains(mouseX, mouseY);
        Color s = new Color(40, 40, 46, 220);
        Color e = new Color(22, 22, 28, 220);
        if (barHover) {
            s = new Color(50, 50, 58, 230);
            e = new Color(28, 28, 36, 230);
            render2DUtil().drawGlow(ctx, bar.x, bar.y, bar.x + bar.w, bar.y + bar.h, new Color(80, 110, 255, 90), BAR_CORNER);
        }
        fillRoundedGradient(ctx, bar, BAR_CORNER, s, e, false);

        float bx = bar.x + PAD;
        Rect tankRect = new Rect(bx, bar.y + PAD, BTN_SIZE, BTN_SIZE);
        Rect dpsRect = new Rect(bx + BTN_SIZE + BTN_GAP, bar.y + PAD, BTN_SIZE, BTN_SIZE);
        Rect healRect = new Rect(bx + (BTN_SIZE + BTN_GAP) * 2f, bar.y + PAD, BTN_SIZE, BTN_SIZE);

        drawRoleButton(ctx, tankRect, Roles.TANK);
        drawRoleButton(ctx, dpsRect, Roles.DPS);
        drawRoleButton(ctx, healRect, Roles.HEAL);

        if (state != UiState.OPTED_OUT) {
            Rect out = new Rect(healRect.x + healRect.w - 1 + 4, bar.y - 4, OPT_OUT_SIZE, OPT_OUT_SIZE);
            boolean hover = out.contains(mouseX, mouseY);
            Color os = hover ? new Color(70, 70, 78, 230) : new Color(64, 64, 72, 220);
            Color oe = hover ? new Color(40, 40, 48, 230) : new Color(36, 36, 44, 220);
            fillRoundedGradient(ctx, out, 2f, os, oe, true);
            render2DUtil().drawTexture(ctx, TextureStorage.opt_out, out.x + 1, out.y + 1, out.x + out.w - 1, out.y + out.h - 1);
        }
    }

    void drawRoleButton(DrawContext ctx, Rect r, Roles role) {
        boolean active = Objects.equals(selectedRole, role);
        TexturePair tp = roleTextures.get(role);
        render2DUtil().drawTexture(ctx, active ? tp.active : tp.inactive, r.x, r.y, r.x + r.w, r.y + r.h);
    }

    void drawRtsBar(DrawContext ctx, int mouseX, int mouseY) {
        float h = RTS_BAR_H;
        Rect panel = new Rect(renderX + PAD, renderY + mapHeight - h - PAD, mapWidth - PAD * 2f, h);
        Color s = new Color(38, 38, 44, 230);
        Color e = new Color(22, 22, 28, 230);
        fillRoundedGradient(ctx, panel, 10f, s, e, true);

        List<String> tabs = new ArrayList<>(groups.keySet());
        float tx = panel.x + RTS_GROUP_TAB_PAD;
        for (String g : tabs) {
            int tw = textRenderer().getWidth(g) + 20;
            Rect tab = new Rect(tx, panel.y + 6, tw, RTS_GROUP_TAB_H);
            boolean on = currentGroup.equals(g);
            Color ts = on ? new Color(70, 110, 255, 240) : new Color(56, 56, 64, 220);
            Color te = on ? new Color(40, 70, 220, 240) : new Color(38, 38, 46, 220);
            fillRoundedGradient(ctx, tab, 6f, ts, te, true);
            render2DUtil().drawText(ctx, g, (int)(tab.x + tw/2f - textRenderer().getWidth(g)/2f), (int)(tab.y + 6), 0xFFFFFF, true);
            tx += tw + 6;
        }

        List<Warrer> visible = getVisibleWarrers();
        float hx = panel.x + RTS_HEAD_PAD;
        float hy = panel.y + RTS_GROUP_TAB_H + 14f;
        float slot = RTS_HEAD + 8f;
        int start = Math.max(0, Math.min(warrerScroll, Math.max(0, visible.size() - 1)));
        for (int i = start; i < visible.size(); i++) {
            Warrer w = visible.get(i);
            if (hx + RTS_HEAD > panel.x + panel.w - RTS_HEAD_PAD) break;
            Rect r = new Rect(hx, hy, RTS_HEAD, RTS_HEAD);
            Color rs = new Color(56, 56, 64, 240);
            Color re = new Color(36, 36, 44, 240);
            if (w.selected) { rs = new Color(90,120,255,240); re = new Color(60,80,220,240); }
            fillRoundedGradient(ctx, r, 8f, rs, re, true);
            drawPlayerHead(ctx, w, r.x+4, r.y+4, RTS_HEAD-8);
            String label = w.name;
            render2DUtil().drawText(ctx, label, (int)(r.x + RTS_HEAD/2f - textRenderer().getWidth(label)/2f), (int)(r.y + RTS_HEAD + 2), 0xFFFFFF, true);
            Identifier badge = w.role==Roles.TANK?TextureStorage.tank_active: w.role==Roles.DPS?TextureStorage.damage_active:TextureStorage.healer_active;
            render2DUtil().drawTexture(ctx, badge, r.x+RTS_HEAD-18, r.y+4, r.x+RTS_HEAD-4, r.y+18);
            if (!w.queue.isEmpty()) {
                String q = String.valueOf(w.queue.size());
                render2DUtil().drawText(ctx, q, (int)(r.x+6), (int)(r.y+6), 0xFFFFFF, true);
            }
            hx += slot;
        }
    }

    void renderRtsDottedLines(MatrixStack m) {
        List<Warrer> sel = groups.getOrDefault(myGroup, List.of()).stream().filter(w->w.selected).collect(Collectors.toList());
        if (sel.isEmpty()) return;
        for (Warrer w : sel) {
            float sx = (float)(centerX + (w.x - mapCenterX) * zoomRenderScale);
            float sz = (float)(centerZ + (w.z - mapCenterZ) * zoomRenderScale);
            List<String> seq = new ArrayList<>(w.queue);
            for (String t : seq) {
                TerritoryPoi poi = getPoiByName(t);
                if (poi == null) continue;
                float ex = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
                float ez = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);
                drawDotted(m, sx, sz, ex, ez, CommonColors.GRAY);
                sx = ex; sz = ez;
            }
        }
    }

    void drawPlayerHead(DrawContext ctx, Warrer w, float x, float y, float s) {
        render2DUtil().drawTexture(ctx, TextureStorage.circle, x, y, x+s, y+s);
    }

    void drawDotted(MatrixStack m, float x1, float y1, float x2, float y2, CustomColor c) {
        int segments = 24;
        for (int i=0;i<segments;i++) {
            if (i%2==0) continue;
            float t1 = (float)i/segments;
            float t2 = (float)(i+1)/segments;
            float sx = x1 + (x2-x1)*t1;
            float sy = y1 + (y2-y1)*t1;
            float ex = x1 + (x2-x1)*t2;
            float ey = y1 + (y2-y1)*t2;
            RenderUtils.drawLine(m, c, sx, sy, ex, ey, 0, 2);
        }
    }

    TerritoryPoi getPoiByName(String name) {
        for (Poi p : Models.Territory.getTerritoryPoisFromAdvancement()) {
            if (p instanceof TerritoryPoi tp && tp.getName().equals(name)) return tp;
        }
        return null;
    }

    String valueText(CappedValue v) {
        if (v == null) return "0/0";
        int c = (int) Math.round(v.current());
        int m = (int) Math.round(v.max());
        return c + "/" + m;
    }

    double valueRatio(CappedValue v) {
        if (v == null) return 0d;
        double max = v.max();
        double val = v.current();
        if (max <= 0d) return 0d;
        return val / max;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && KeyboardUtils.isShiftDown() && hovered instanceof TerritoryPoi territoryPoi) {
                Handlers.Command.queueCommand("gu territory " + territoryPoi.getName());
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        float baseX = renderX + renderedBorderXOffset;
        float baseY = renderY + renderedBorderYOffset;

        float optBaseX = renderX + renderedBorderXOffset + mapWidth / 2f;
        Rect optRect = new Rect(optBaseX - CMD_BTN_SIZE / 2f, baseY + PAD, CMD_BTN_SIZE, CMD_BTN_SIZE);
        if (optRect.contains(mouseX, mouseY)) { optedIn = !optedIn; return true; }

        if (hasSelectedFromMyGroup() && clickCommandBox(mouseX, mouseY)) return true;
        if (optedIn && clickGroupTabs(mouseX, mouseY)) return true;
        if (optedIn && clickHeads(mouseX, mouseY)) return true;

        if (state == UiState.OPTED_OUT) {
            Rect optIn = new Rect(baseX + PAD, baseY + PAD, OPT_BTN_SIZE, OPT_BTN_SIZE);
            if (optIn.contains(mouseX, mouseY)) { state = UiState.CHOOSING_ROLE; selectedRole = null; return true; }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        float barWidth = PAD + (BTN_SIZE * 3f) + (BTN_GAP * 2f) + PAD;
        Rect bar = new Rect(baseX, baseY + PAD, barWidth, BAR_HEIGHT);

        Rect tankRect = new Rect(bar.x + PAD, bar.y + PAD, BTN_SIZE, BTN_SIZE);
        Rect dpsRect = new Rect(bar.x + PAD + BTN_SIZE + BTN_GAP, bar.y + PAD, BTN_SIZE, BTN_SIZE);
        Rect healRect = new Rect(bar.x + PAD + (BTN_SIZE + BTN_GAP) * 2f, bar.y + PAD, BTN_SIZE, BTN_SIZE);

        if (state == UiState.CHOOSING_ROLE) {
            if (tankRect.contains(mouseX, mouseY)) { selectedRole = Roles.TANK; state = UiState.CHOSEN_ROLE; return true; }
            if (dpsRect.contains(mouseX, mouseY)) { selectedRole = Roles.DPS; state = UiState.CHOSEN_ROLE; return true; }
            if (healRect.contains(mouseX, mouseY)) { selectedRole = Roles.HEAL; state = UiState.CHOSEN_ROLE; return true; }
        }

        if (state != UiState.OPTED_OUT) {
            Rect out = new Rect(healRect.x + healRect.w - 1 + 4, bar.y - 4, OPT_OUT_SIZE, OPT_OUT_SIZE);
            if (out.contains(mouseX, mouseY)) { state = UiState.OPTED_OUT; selectedRole = null; return true; }
        }

        if (hovered instanceof TerritoryPoi tp && hasSelectedFromMyGroup()) { issueCommandToSelection(tp.getName()); return true; }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    boolean hasSelectedFromMyGroup() {
        List<Warrer> mine = groups.getOrDefault(myGroup, List.of());
        for (Warrer w : mine) if (w.selected) return true;
        return false;
    }

    boolean clickCommandBox(double mouseX, double mouseY) {
        float x2 = renderX + renderedBorderXOffset + mapWidth - PAD;
        float y2 = renderY + renderedBorderYOffset + mapHeight - PAD;
        Rect panel = new Rect(x2 - CMD_BOX_W, y2 - CMD_BOX_H - (optedIn ? RTS_BAR_H + PAD : 0), CMD_BOX_W, CMD_BOX_H);
        float bx = panel.x + 10;
        float by = panel.y + (panel.h - CMD_BTN_SIZE) / 2f;
        Rect move = new Rect(bx, by, CMD_BTN_SIZE, CMD_BTN_SIZE);
        Rect attack = new Rect(bx + CMD_BTN_SIZE + 8, by, CMD_BTN_SIZE, CMD_BTN_SIZE);
        Rect sw = new Rect(bx + (CMD_BTN_SIZE + 8) * 2, by, CMD_BTN_SIZE, CMD_BTN_SIZE);
        if (move.contains(mouseX, mouseY)) { currentCommand = Command.MOVE; return true; }
        if (attack.contains(mouseX, mouseY)) { currentCommand = Command.ATTACK; return true; }
        if (sw.contains(mouseX, mouseY)) {
            if (KeyboardUtils.isShiftDown()) {
                switchRequestRole = switchRequestRole==Roles.TANK?Roles.DPS:switchRequestRole==Roles.DPS?Roles.HEAL:Roles.TANK;
            }
            currentCommand = Command.SWITCH_CLASS;
            return true;
        }
        return false;
    }

    boolean clickGroupTabs(double mouseX, double mouseY) {
        float h = RTS_BAR_H;
        Rect panel = new Rect(renderX + PAD, renderY + mapHeight - h - PAD, mapWidth - PAD * 2f, h);
        float tx = panel.x + RTS_GROUP_TAB_PAD;
        for (String g : groups.keySet()) {
            int tw = textRenderer().getWidth(g) + 20;
            Rect tab = new Rect(tx, panel.y + 6, tw, RTS_GROUP_TAB_H);
            if (tab.contains(mouseX, mouseY)) { currentGroup = g; return true; }
            tx += tw + 6;
        }
        return false;
    }

    boolean clickHeads(double mouseX, double mouseY) {
        float h = RTS_BAR_H;
        Rect panel = new Rect(renderX + PAD, renderY + mapHeight - h - PAD, mapWidth - PAD * 2f, h);
        float hx = panel.x + RTS_HEAD_PAD;
        float hy = panel.y + RTS_GROUP_TAB_H + 14f;
        float slot = RTS_HEAD + 8f;
        List<Warrer> visible = getVisibleWarrers();
        int start = Math.max(0, Math.min(warrerScroll, Math.max(0, visible.size() - 1)));
        for (int i = start; i < visible.size(); i++) {
            Warrer w = visible.get(i);
            if (hx + RTS_HEAD > panel.x + panel.w - RTS_HEAD_PAD) break;
            Rect r = new Rect(hx, hy, RTS_HEAD, RTS_HEAD);
            if (r.contains(mouseX, mouseY)) {
                if (!KeyboardUtils.isShiftDown()) groups.values().forEach(list -> list.forEach(x->x.selected=false));
                w.selected = !w.selected;
                return true;
            }
            hx += slot;
        }
        return false;
    }

    void issueCommandToSelection(String territory) {
        List<Warrer> sel = groups.getOrDefault(myGroup, List.of()).stream().filter(w->w.selected).collect(Collectors.toList());
        if (sel.isEmpty()) return;
        if (currentCommand == Command.MOVE) {
            for (Warrer w : sel) { w.queue.clear(); w.queue.addLast(territory); }
            wsMove(sel, territory);
        } else if (currentCommand == Command.ATTACK) {
            for (Warrer w : sel) { w.queue.clear(); w.queue.addLast(territory); }
            wsAttack(sel, territory);
        } else if (currentCommand == Command.SWITCH_CLASS) {
            wsSwitchClass(sel, switchRequestRole);
        }
    }

    protected void renderMap(DrawContext context) {
        MatrixStack poseStack = context.getMatrices();

        render2DUtil().enableScissor((int)(renderX + renderedBorderXOffset), (int)(renderY + renderedBorderYOffset), (int)(renderX + renderedBorderXOffset + mapWidth), (int)(renderY + renderedBorderYOffset + mapHeight));
        RenderUtils.drawRect(poseStack, CommonColors.BLACK, renderX + renderedBorderXOffset, renderY + renderedBorderYOffset, 0, mapWidth, mapHeight);

        BoundingBox textureBoundingBox = BoundingBox.centered(mapCenterX, mapCenterZ, width / zoomRenderScale, height / zoomRenderScale);
        List<MapTexture> maps = Services.Map.getMapsForBoundingBox(textureBoundingBox);

        for (MapTexture map : maps) {
            float textureX = map.getTextureXPosition(mapCenterX);
            float textureZ = map.getTextureZPosition(mapCenterZ);
            MapRenderer.renderMapQuad(map, poseStack, BUFFER_SOURCE, centerX, centerZ, textureX, textureZ, mapWidth, mapHeight, 1f / zoomRenderScale);
        }

        BUFFER_SOURCE.draw();
        render2DUtil().disableScissor();
    }

    private void renderPois(MatrixStack matrixStack, int mouseX, int mouseY) {
        List<TerritoryPoi> advancementPois = Models.Territory.getTerritoryPoisFromAdvancement();
        List<Poi> renderedPois = new ArrayList<>();

        for (TerritoryPoi poi : advancementPois) {
            TerritoryProfile territoryProfile = Models.Territory.getTerritoryProfile(poi.getName());
            if (territoryProfile != null && territoryProfile.getGuild().equals(poi.getTerritoryInfo().getGuildName())) {
                if (poi.getTerritoryInfo().isHeadquarters() && poi.getTerritoryInfo().getGuildName().equals("Sequoia")) {
                    ore = poi.getTerritoryInfo().getStorage(ORE);
                    fish = poi.getTerritoryInfo().getStorage(FISH);
                    wood = poi.getTerritoryInfo().getStorage(WOOD);
                    crop = poi.getTerritoryInfo().getStorage(CROPS);
                }
                renderedPois.add(poi);
            } else {
                renderedPois.add(new TerritoryPoi(territoryProfile, poi.getTerritoryInfo()));
            }
        }

        Models.Marker.USER_WAYPOINTS_PROVIDER.getPois().forEach(renderedPois::add);

        renderPois(renderedPois, matrixStack, BoundingBox.centered(mapCenterX, mapCenterZ, width / zoomRenderScale, (height - (optedIn ? RTS_BAR_H : 0)) / zoomRenderScale), 1, mouseX, mouseY);
    }

    protected void renderPois(List<Poi> pois, MatrixStack matrixStack, BoundingBox textureBoundingBox, float poiScale, int mouseX, int mouseY) {
        hovered = null;

        List<Poi> filteredPois = getRenderedPois(pois, textureBoundingBox, poiScale, mouseX, mouseY);

        for (Poi poi : filteredPois) {
            if (!(poi instanceof TerritoryPoi territoryPoi)) continue;

            float poiRenderX = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
            float poiRenderZ = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);

            for (String tradingRoute : territoryPoi.getTerritoryInfo().getTradingRoutes()) {
                Optional<Poi> routePoi = filteredPois.stream().filter(filteredPoi -> filteredPoi.getName().equals(tradingRoute)).findFirst();

                if (routePoi.isPresent() && filteredPois.contains(routePoi.get())) {
                    float x = MapRenderer.getRenderX(routePoi.get(), mapCenterX, centerX, zoomRenderScale);
                    float z = MapRenderer.getRenderZ(routePoi.get(), mapCenterZ, centerZ, zoomRenderScale);
                    RenderUtils.drawLine(matrixStack, CommonColors.DARK_GRAY, poiRenderX, poiRenderZ, x, z, 0, 1);
                }
            }
        }

        VertexConsumerProvider.Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();

        for (int i = filteredPois.size() - 1; i >= 0; i--) {
            Poi poi = filteredPois.get(i);

            float poiRenderX = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
            float poiRenderZ = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);

            poi.renderAt(matrixStack, bufferSource, poiRenderX, poiRenderZ, hovered == poi, poiScale, zoomRenderScale, zoomLevel, true);
        }

        bufferSource.drawCurrentLayer();
    }

    protected List<Poi> getRenderedPois(List<Poi> pois, BoundingBox textureBoundingBox, float poiScale, int mouseX, int mouseY) {
        List<Poi> filteredPois = new ArrayList<>();

        for (int i = pois.size() - 1; i >= 0; --i) {
            Poi poi = pois.get(i);
            if (poi.getLocation() != null && poi.isVisible(zoomRenderScale, zoomLevel)) {
                float poiRenderX = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
                float poiRenderZ = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);
                float poiWidth = (float) poi.getWidth(zoomRenderScale, poiScale);
                float poiHeight = (float) poi.getHeight(zoomRenderScale, poiScale);
                BoundingBox filterBox = BoundingBox.centered((float) poi.getLocation().getX(), (float) poi.getLocation().getZ(), poiWidth, poiHeight);
                BoundingBox mouseBox = BoundingBox.centered(poiRenderX, poiRenderZ, poiWidth, poiHeight);
                if (BoundingShape.intersects(filterBox, textureBoundingBox)) {
                    filteredPois.add(poi);
                    if (hovered == null && mouseBox.contains((float) mouseX, (float) mouseY)) hovered = poi;
                }
            }
        }

        if (hovered != null) {
            filteredPois.remove(hovered);
            filteredPois.add(0, hovered);
        }
        return filteredPois;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (optedIn) {
            float h = RTS_BAR_H;
            Rect panel = new Rect(renderX + PAD, renderY + mapHeight - h - PAD, mapWidth - PAD * 2f, h);
            if (panel.contains(mouseX, mouseY)) { warrerScroll = Math.max(0, warrerScroll - (int)Math.signum(deltaY)); return true; }
        }
        adjustZoomLevel((float)(2.0F * deltaY));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.close(); return true; }
        else if (keyCode != 61 && keyCode != 334) {
            if (keyCode != 45 && keyCode != 333) {
                InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
                KeyBinding.setKeyPressed(key, true);
                if (keyCode == GLFW.GLFW_KEY_1) { currentCommand = Command.MOVE; return true; }
                if (keyCode == GLFW.GLFW_KEY_2) { currentCommand = Command.ATTACK; return true; }
                if (keyCode == GLFW.GLFW_KEY_3) { currentCommand = Command.SWITCH_CLASS; return true; }
                if (keyCode == GLFW.GLFW_KEY_T) { switchRequestRole = Roles.TANK; return true; }
                if (keyCode == GLFW.GLFW_KEY_Y) { switchRequestRole = Roles.DPS; return true; }
                if (keyCode == GLFW.GLFW_KEY_U) { switchRequestRole = Roles.HEAL; return true; }
                if (keyCode == GLFW.GLFW_KEY_Q && hasSelectedFromMyGroup()) { queueSelectedToHovered(); return true; }
                if (keyCode == GLFW.GLFW_KEY_R && hasSelectedFromMyGroup()) { clearQueuesSelected(); return true; }
                return false;
            } else { adjustZoomLevel(-2.0F); return true; }
        } else { adjustZoomLevel(2.0F); return true; }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
        KeyBinding.setKeyPressed(key, false);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && mouseX >= this.renderX && mouseX <= this.renderX + this.renderWidth && mouseY >= this.renderY && mouseY <= this.renderY + this.renderHeight - (optedIn ? RTS_BAR_H : 0)) {
            this.updateMapCenter((float)(this.mapCenterX - dragX / this.zoomRenderScale), (float)(this.mapCenterZ - dragY / this.zoomRenderScale));
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    protected void setZoomLevel(float zoomLevel) {
        this.zoomLevel = Math.max(1.0F, Math.min(100.0F, zoomLevel));
        this.zoomRenderScale = MapRenderer.getZoomRenderScaleFromLevel(this.zoomLevel);
    }

    private void adjustZoomLevel(float delta) { this.setZoomLevel(this.zoomLevel + delta); }

    protected void updateMapCenter(float newX, float newZ) { this.mapCenterX = newX; this.mapCenterZ = newZ; }

    List<Warrer> getVisibleWarrers() {
        if (currentGroup.equals("All")) {
            List<Warrer> all = new ArrayList<>();
            for (Map.Entry<String,List<Warrer>> e : groups.entrySet()) if (!e.getKey().equals("All")) all.addAll(e.getValue());
            groups.get("All").clear();
            groups.get("All").addAll(all);
            return groups.get("All");
        }
        return groups.getOrDefault(currentGroup, Collections.emptyList());
    }

    void queueSelectedToHovered() {
        if (!(hovered instanceof TerritoryPoi tp)) return;
        List<Warrer> sel = groups.getOrDefault(myGroup, List.of()).stream().filter(w->w.selected).collect(Collectors.toList());
        if (sel.isEmpty()) return;
        for (Warrer w : sel) w.queue.addLast(tp.getName());
        wsQueue(sel, tp.getName());
    }

    void clearQueuesSelected() {
        List<Warrer> sel = groups.getOrDefault(myGroup, List.of()).stream().filter(w->w.selected).collect(Collectors.toList());
        if (sel.isEmpty()) return;
        for (Warrer w : sel) w.queue.clear();
        wsClearQueue(sel);
    }

    void seedDemoWarrers() {
        if (groups.get("Group A").isEmpty() && groups.get("Group B").isEmpty()) {
            groups.get("Group A").add(new Warrer(UUID.randomUUID(), "Oak", Roles.TANK, "Group A"));
            groups.get("Group A").add(new Warrer(UUID.randomUUID(), "Birch", Roles.DPS, "Group A"));
            groups.get("Group A").add(new Warrer(UUID.randomUUID(), "Spruce", Roles.HEAL, "Group A"));
            groups.get("Group B").add(new Warrer(UUID.randomUUID(), "Maple", Roles.DPS, "Group B"));
            groups.get("Group B").add(new Warrer(UUID.randomUUID(), "Pine", Roles.TANK, "Group B"));
            Random r = new Random();
            for (List<Warrer> list : groups.values()) {
                if (list==groups.get("All")) continue;
                for (Warrer w : list) { w.x = mapCenterX + r.nextInt(200) - 100; w.z = mapCenterZ + r.nextInt(200) - 100; }
            }
        }
    }

    void wsAssign(Collection<Warrer> ws, Roles role, String controllerId) {}
    void wsPositions(Collection<Warrer> ws) {}
    void wsAttack(Collection<Warrer> ws, String territory) {}
    void wsMove(Collection<Warrer> ws, String territory) {}
    void wsSwitchClass(Collection<Warrer> ws, Roles role) {}
    void wsDecline(UUID warrerId, String territory) {}
    void wsQueue(Collection<Warrer> ws, String territory) {}
    void wsClearQueue(Collection<Warrer> ws) {}
}
