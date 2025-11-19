package star.sequoia2.gui.categories.impl;

import com.wynntils.utils.colors.CustomColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.features.impl.PartyFinder;
import star.sequoia2.gui.categories.RelativeComponent;
import star.sequoia2.gui.component.ModuleButton;
import star.sequoia2.gui.component.PartyButton;
import star.sequoia2.gui.screen.GuiRoot;

import java.util.ArrayList;
import java.util.List;

public class PartyFinderCategory extends RelativeComponent implements RenderUtilAccessor {
    PartyFinder.Party dummyParty = new PartyFinder.Party(List.of("Player 1", "Player 2", "Player 3", "Player 4"));
    private final List<PartyButton> partyButtons = new ArrayList<>();

    public PartyFinderCategory() {
        super("Party Finder");

        for (int i = 0; i < 3; i++) {
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

        matrices.push();
        context.enableScissor((int) viewportX, (int) viewportY, (int) (viewportX + viewportW), (int) (viewportY + viewportH));

        float drawOffset = 0f;
        for (PartyButton button : partyButtons) {
            float itemH = root.btnH * 2;
            float yy = viewportY + drawOffset + root.btnGap;
            button.setPos(left, yy);
            button.setDimensions(viewportW, itemH);
            button.render(context, mouseX, mouseY, delta);

            drawOffset += itemH + root.btnGap;
            if (button.isOpen()) {
                float eh = button.getExpandedHeight();
                drawOffset += eh;
            }
        }

        context.disableScissor();
        matrices.pop();
    }
}
