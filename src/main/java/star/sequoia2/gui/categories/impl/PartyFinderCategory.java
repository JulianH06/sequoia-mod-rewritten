package star.sequoia2.gui.categories.impl;

import com.wynntils.utils.colors.CustomColor;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.client.types.Services;
import star.sequoia2.features.impl.PartyFinder;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.categories.RelativeComponent;
import star.sequoia2.gui.component.ModuleButton;
import star.sequoia2.gui.component.PartyButton;
import star.sequoia2.gui.component.PartyCreateButton;
import star.sequoia2.gui.component.SearchBarComponent;
import star.sequoia2.gui.screen.GuiRoot;

import java.util.ArrayList;
import java.util.List;

public class PartyFinderCategory extends RelativeComponent implements RenderUtilAccessor {
    SearchBarComponent searchBarComponent;

    PartyFinder.Party dummyParty = new PartyFinder.Party(List.of("Player 1", "Player 2", "Player 3"), 4);
    private final List<PartyButton> partyButtons = new ArrayList<>();
    private final PartyCreateButton partyCreateButton = new PartyCreateButton();
    public static boolean isCreatingParty = false;
    private float scrollOffset = 0f;
    private float targetScrollOffset = 0f;
    private boolean draggingScrollbar = false;
    private float dragThumbOffset = 0f;

    public PartyFinderCategory() {
        super("Party Finder");
        searchBarComponent = new SearchBarComponent() {
            @Override
            public void render(DrawContext context, float mouseX, float mouseY, float delta) {
                int textColor = 0xFFFFFFFF;
                String display;
                GuiRoot root = getGuiRoot();
                float left = contentX();
                float top = contentY();
                float right = left + contentWidth();
                float bottom = top + contentHeight();

                context.enableScissor((int) left, (int) top, (int) right, (int) bottom);

                boolean showCaret = searching && ((System.currentTimeMillis() / 500L) % 2L == 0L);

                if (searching) {
                    display = search.isEmpty() ? "" : search;
                    features().get(Settings.class).map(Settings::getClickGui).orElseThrow().setCloseOnEscape(false);
                } else {
                    display = "§8Click to search";
                    features().get(Settings.class).map(Settings::getClickGui).orElseThrow().setCloseOnEscape(true);
                }

                float scale = 1.5f;

                context.getMatrices().push();
                context.getMatrices().translate(contentX() + (contentWidth() / 2) - (textRenderer().getWidth(display)), contentY() + (contentHeight() / 2) - textRenderer().fontHeight, 0);
                context.getMatrices().scale(scale, scale, 1.0f);

                render2DUtil().drawText(context, display + (showCaret ? "_" : ""), 20, 2, textColor, true);

                context.getMatrices().pop();

                context.disableScissor();
            }
        };
        isCreatingParty = false;

        for (int i = 0; i < 2; i++) {
            partyButtons.add(new PartyButton(dummyParty));
        }
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        float left = contentX();
        float top = contentY();
        float right = left + contentWidth();
        float bottom = top + contentHeight();
        MatrixStack matrices = context.getMatrices();

        Color light = features().get(Settings.class).map(Settings::getThemeLight).orElse(Color.black());
        Color normal = features().get(Settings.class).map(Settings::getThemeNormal).orElse(Color.black());
        Color dark = features().get(Settings.class).map(Settings::getThemeDark).orElse(Color.black());
        Color accent1 = features().get(Settings.class).map(Settings::getThemeAccent1).orElse(Color.black());
        Color accent2 = features().get(Settings.class).map(Settings::getThemeAccent2).orElse(Color.black());

        if(!PartyFinderCategory.isCreatingParty) searchBarComponent.render(context, mouseX, mouseY, delta);
        searchBarComponent.setPos(left, top);
        searchBarComponent.setDimensions(contentWidth() - 25f - 90, getGuiRoot().btnH);

        float x = left;
        float y = top + getGuiRoot().btnH + 6f;
        float line = textRenderer().fontHeight + 4f;

//        for (int i = 0; i < 3; i++) {
//            render2DUtil().drawText(context, "Party " + i, x, y, CustomColor.fromHexString("FFFFFF").asInt(), true);
//            y += line;
//        }
        GuiRoot root = getGuiRoot();
        float trackPad = 6f;
        float trackW = 2f;
        float viewportX = left;
        float viewportY = top + root.btnH;
        float viewportW = contentWidth() - trackW - 4f;
        float viewportH = contentHeight() - root.btnH;
        float trackX = right - trackW;
        float trackY = viewportY + trackPad;
        float trackH = Math.max(0f, viewportH - trackPad * 2f);

        float totalContent = totalContentHeight(root);
        float maxOffset = Math.max(0f, totalContent - viewportH);
        if (targetScrollOffset > maxOffset * 2) targetScrollOffset = maxOffset;
        if (targetScrollOffset < 0f) targetScrollOffset = 0f;

        float k = 0.18f;
        if (draggingScrollbar) {
            scrollOffset = targetScrollOffset;
        } else {
            scrollOffset += (targetScrollOffset - scrollOffset) * k;
        }

        matrices.push();
        context.enableScissor((int) viewportX, (int) viewportY, (int) (viewportX + viewportW), (int) (viewportY + viewportH));

        if(!isCreatingParty) {
            float drawOffset = 0f;
            for (PartyButton button : partyButtons) {
                float itemH = root.btnH * 2 + button.extraHeight + 25;
                float yy = viewportY + drawOffset - scrollOffset + root.btnGap;
                button.setPos(left, yy);
                button.setDimensions(viewportW, itemH);
                button.render(context, mouseX, mouseY, delta);

                drawOffset += itemH + root.btnGap;
                if (button.isOpen()) {
                    float eh = button.getExpandedHeight();
                    drawOffset += eh;
                }
            }
        }

        context.disableScissor();

        {
            float itemH = root.btnH * 2;
            float yy = viewportY - scrollOffset + root.btnGap;
            partyCreateButton.setPos(left + width - 95 - 8, top);
            partyCreateButton.setDimensions(95, getGuiRoot().btnH);
            partyCreateButton.render(context, mouseX, mouseY, delta);
        }

        matrices.pop();

        if (totalContent > viewportH && trackH > 0f) {
            float thumbH = 20f;
            float available = Math.max(0f, trackH - thumbH);
            float thumbY = trackY + (maxOffset == 0 ? 0 : (scrollOffset / maxOffset) * available);

            boolean overThumb = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            Color scrollColor = overThumb || draggingScrollbar ? light : accent2;

            render2DUtil().roundRectFilled(matrices, trackX, thumbY, trackX + trackW, thumbY + thumbH, 0.5f, scrollColor);
        }
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        GuiRoot root = getGuiRoot();
        if (root != null && button == 0) {
            float left = contentX();
            float top = contentY();
            float right = left + contentWidth();
            float trackW = 2f;
            float trackPad = 6f;
            float viewportY = top + root.btnH;
            float viewportH = contentHeight() - root.btnH;
            float trackX = right - trackW;
            float trackY = viewportY + trackPad;
            float trackH = Math.max(0f, viewportH - trackPad * 2f);

            float totalContent = totalContentHeight(root);
            float maxOffset = Math.max(0f, totalContent - viewportH);
            if (totalContent > viewportH && trackH > 0f) {
                float thumbH = 20f;
                float available = Math.max(0f, trackH - thumbH);
                float thumbY = trackY + (maxOffset == 0 ? 0 : (scrollOffset / maxOffset) * available);
                boolean overThumb = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= thumbY && mouseY <= thumbY + thumbH;

                draggingScrollbar = false;
                if (overThumb) {
                    draggingScrollbar = true;
                    targetScrollOffset = scrollOffset;
                    dragThumbOffset = mouseY - thumbY;
                    if (dragThumbOffset < 0f) dragThumbOffset = 0f;
                    if (dragThumbOffset > thumbH) dragThumbOffset = thumbH;
                } else if (mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= trackY && mouseY <= trackY + trackH) {
                    float desiredThumbY = Math.max(trackY, Math.min(trackY + available, mouseY - thumbH * 0.5f));
                    float ratio = available == 0f ? 0f : (desiredThumbY - trackY) / available;
                    targetScrollOffset = Math.max(0f, Math.min(maxOffset, ratio * maxOffset));
                    draggingScrollbar = true;
                    dragThumbOffset = thumbH * 0.5f;
                }
            }
        }

        partyCreateButton.mouseClicked(mouseX, mouseY, button);
        if(!PartyFinderCategory.isCreatingParty) {
            searchBarComponent.mouseClicked(mouseX, mouseY, button);
            for (PartyButton partyButton : partyButtons) {
                partyButton.mouseClicked(mouseX, mouseY, button);
            }
        }
        float left = contentX();
        float top = contentY();
        float right = left + contentWidth();
        if (isWithin(mouseX, mouseY, right - 25f, top, 25, getGuiRoot().btnH) && searchBarComponent.isSearching()) {
            searchBarComponent.setSearching(false);
            searchBarComponent.setSearch("");
        }
    }

    @Override
    public void mouseMoved(float mouseX, float mouseY) {
        if (draggingScrollbar) {
            GuiRoot root = getGuiRoot();
            if (root != null) {
                float left = contentX();
                float top = contentY();
                float right = left + contentWidth();
                float trackW = 2f;
                float trackPad = 6f;
                float viewportY = top + root.btnH;
                float viewportH = contentHeight() - root.btnH;
                float trackY = viewportY + trackPad;
                float trackH = Math.max(0f, viewportH - trackPad * 2f);
                float totalContent = totalContentHeight(root);
                float maxOffset = Math.max(0f, totalContent - viewportH);
                if (totalContent > viewportH && trackH > 0f && maxOffset > 0f) {
                    float thumbH = 20f;
                    float available = trackH - thumbH;
                    float by = top - root.pad;
                    float globalMouseY = mouseY + by;
                    float desiredThumbY = globalMouseY - dragThumbOffset;
                    float minY = trackY;
                    float maxY = trackY + available;
                    if (desiredThumbY < minY) desiredThumbY = minY;
                    if (desiredThumbY > maxY) desiredThumbY = maxY;
                    float ratio = available == 0f ? 0f : (desiredThumbY - trackY) / available;
                    targetScrollOffset = Math.max(0f, Math.min(maxOffset, ratio * maxOffset));
                }
            }
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (button == 0) draggingScrollbar = false;
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        searchBarComponent.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ENTER && searchBarComponent.isSearching()) {
            searchBarComponent.setSearching(false);
            searchBarComponent.setSearch("");
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        searchBarComponent.charTyped(chr, modifiers);
    }

    @Override
    public void mouseScrolled(float mouseX, float mouseY, double horizontalAmount, double verticalAmount) {
        GuiRoot root = getGuiRoot();
        if (root != null) {
            float viewportH = contentHeight() - root.btnH;
            float totalContent = totalContentHeight(root);
            float maxOffset = Math.max(0f, totalContent - viewportH);
            float step = root.btnH;
            targetScrollOffset = Math.max(0f, Math.min(maxOffset, targetScrollOffset - (float) verticalAmount * step));
        }

//        for (ModuleButton moduleButton : moduleButtons) {
//            if (searchBarComponent.isSearching() && !moduleButton.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
//            moduleButton.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
//        }
    }

    private float totalContentHeight(GuiRoot root) {
        float h = 0f;
        for (PartyButton b : partyButtons) {
            if (searchBarComponent.isSearching() && !b.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            h += root.btnH * 2 + root.btnGap + b.extraHeight + 25;
            if (b.isOpen()) h += b.getExpandedHeight();
        }
        return h + root.btnGap;
    }
}
