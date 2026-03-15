package de.njlent.wurstmeteor.modules.world.autolibrarian;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;
import net.minecraft.util.math.MathHelper;

import static meteordevelopment.meteorclient.utils.Utils.getWindowHeight;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class ScrollableDropdown<T> extends WDropdown<T> implements MeteorWidget {
    private final int maxVisibleValues;

    private boolean openUpwards;

    public ScrollableDropdown(T[] values, T value, int maxVisibleValues) {
        super(values, value);

        this.maxVisibleValues = Math.max(1, maxVisibleValues);
    }

    @Override
    protected WDropdownRoot createRootWidget() {
        return new WRoot();
    }

    @Override
    protected WDropdownValue createValueWidget() {
        return new WValue();
    }

    @Override
    protected void onCalculateWidgetPositions() {
        super.onCalculateWidgetPositions();

        double margin = theme.scale(8);
        double naturalHeight = root.height;
        double rowHeight = values.length > 0 ? naturalHeight / values.length : theme.textHeight() + theme.scale(6);
        double preferredHeight = rowHeight * Math.min(values.length, maxVisibleValues) + margin / 2;

        double spaceBelow = Math.max(0, getWindowHeight() - (y + height) - margin);
        double spaceAbove = Math.max(0, y - margin);

        openUpwards = spaceBelow < Math.min(naturalHeight, preferredHeight) && spaceAbove > spaceBelow;

        WRoot actualRoot = (WRoot) root;
        actualRoot.maxHeight = Math.max(theme.scale(48), Math.min(naturalHeight, openUpwards ? spaceAbove : spaceBelow));
        actualRoot.calculateSize();
        actualRoot.width = width;

        actualRoot.x = x;
        actualRoot.y = openUpwards
            ? Math.max(margin, y - actualRoot.height)
            : Math.min(y + height, getWindowHeight() - margin - actualRoot.height);
        actualRoot.calculateWidgetPositions();
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!visible) return true;

        onRender(renderer, mouseX, mouseY, delta);

        animProgress += (expanded ? 1 : -1) * delta * 14;
        animProgress = MathHelper.clamp(animProgress, 0, 1);

        if (animProgress > 0) {
            renderer.absolutePost(() -> {
                double clipHeight = root.height * animProgress;
                double clipY = openUpwards ? root.y + root.height - clipHeight : root.y;

                renderer.scissorStart(root.x, clipY, width, clipHeight);
                root.render(renderer, mouseX, mouseY, delta);
                renderer.scissorEnd();
            });
        }

        if (expanded && root.mouseOver) theme.disableHoverColor = true;

        return false;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double pad = pad();
        double size = theme.textHeight();

        renderBackground(renderer, this, pressed, mouseOver);

        String text = get().toString();
        double textWidth = theme.textWidth(text);
        renderer.text(text, x + pad + maxValueWidth / 2 - textWidth / 2, y + pad, theme.textColor.get(), false);

        renderer.rotatedQuad(x + pad + maxValueWidth + pad, y + pad, size, size, 0, GuiRenderer.TRIANGLE, theme.textColor.get());
    }

    private class WValue extends WDropdownValue implements MeteorWidget {
        @Override
        protected void onCalculateSize() {
            double pad = pad();

            width = pad + theme.textWidth(value.toString()) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            MeteorGuiTheme theme = theme();

            Color color = theme.backgroundColor.get(pressed, mouseOver, true);
            int alpha = color.a;
            color.a += color.a / 2;
            color.validate();

            renderer.quad(this, color);
            color.a = alpha;

            String text = value.toString();
            renderer.text(text, x + width / 2 - theme.textWidth(text) / 2, y + pad(), theme.textColor.get(), false);
        }
    }

    private static class WRoot extends WDropdownRoot implements MeteorWidget {
        private double maxHeight = Double.MAX_VALUE;
        private boolean canScroll;
        private double actualHeight;
        private double scroll;
        private double targetScroll;
        private boolean moveAfterPositionWidgets;
        private boolean handleMouseOver;
        private boolean draggingHandle;

        @Override
        protected void onCalculateSize() {
            boolean couldScroll = canScroll;
            canScroll = false;
            widthRemove = 0;

            super.onCalculateSize();

            if (height > maxHeight) {
                actualHeight = height;
                height = maxHeight;
                canScroll = true;

                widthRemove = handleWidth() * 2;
                width += widthRemove;

                if (couldScroll) moveAfterPositionWidgets = true;
            } else {
                actualHeight = height;
                scroll = 0;
                targetScroll = 0;
            }
        }

        @Override
        protected void onCalculateWidgetPositions() {
            super.onCalculateWidgetPositions();

            if (moveAfterPositionWidgets) {
                scroll = MathHelper.clamp(scroll, 0, actualHeight - height);
                targetScroll = scroll;
                moveCells(0, -scroll);
                moveAfterPositionWidgets = false;
            }
        }

        @Override
        public boolean onMouseClicked(Click click, boolean doubled) {
            if (handleMouseOver && click.button() == GLFW_MOUSE_BUTTON_LEFT && !doubled) {
                draggingHandle = true;
                return true;
            }

            return false;
        }

        @Override
        public boolean onMouseReleased(Click click) {
            draggingHandle = false;
            return false;
        }

        @Override
        public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
            handleMouseOver = false;

            if (canScroll) {
                double handleX = handleX();
                double handleY = handleY();

                if (mouseX >= handleX && mouseX <= handleX + handleWidth() && mouseY >= handleY && mouseY <= handleY + handleHeight()) {
                    handleMouseOver = true;
                }
            }

            if (draggingHandle) {
                double before = scroll;
                double mouseDelta = mouseY - lastMouseY;

                scroll += Math.round(mouseDelta * ((actualHeight - handleHeight() / 2) / height));
                scroll = MathHelper.clamp(scroll, 0, actualHeight - height);
                targetScroll = scroll;

                double delta = scroll - before;
                if (delta != 0) moveCells(0, -delta);
            }
        }

        @Override
        public boolean onMouseScrolled(double amount) {
            if (!mouseOver) return false;

            double max = actualHeight - height;
            targetScroll -= Math.round(theme.scale(amount * 40));
            targetScroll = MathHelper.clamp(targetScroll, 0, max);

            return max > 0;
        }

        @Override
        public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            updateScroll(delta);

            if (canScroll) renderer.scissorStart(x, y, width, height);
            boolean render = super.render(renderer, mouseX, mouseY, delta);
            if (canScroll) renderer.scissorEnd();

            return render;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            MeteorGuiTheme theme = theme();
            double size = theme.scale(2);
            Color color = theme.outlineColor.get();

            renderer.quad(x, y + height - size, width, size, color);
            renderer.quad(x, y, size, height - size, color);
            renderer.quad(x + width - size, y, size, height - size, color);

            if (canScroll) {
                renderer.quad(handleX(), handleY(), handleWidth(), handleHeight(), theme.scrollbarColor.get(draggingHandle, handleMouseOver));
            }
        }

        @Override
        protected boolean propagateEvents(WWidget widget) {
            if (widget.isFocused()) return true;
            return mouseOver && isWidgetInView(widget);
        }

        private void updateScroll(double delta) {
            double before = scroll;
            double max = actualHeight - height;

            if (Math.abs(targetScroll - scroll) < 1) {
                scroll = targetScroll;
            } else if (targetScroll > scroll) {
                scroll += Math.round(theme.scale(delta * 300 + delta * 100 * (Math.abs(targetScroll - scroll) / 10)));
                if (scroll > targetScroll) scroll = targetScroll;
            } else if (targetScroll < scroll) {
                scroll -= Math.round(theme.scale(delta * 300 + delta * 100 * (Math.abs(targetScroll - scroll) / 10)));
                if (scroll < targetScroll) scroll = targetScroll;
            }

            scroll = MathHelper.clamp(scroll, 0, max);

            double change = scroll - before;
            if (change != 0) moveCells(0, -change);
        }

        private double handleWidth() {
            return theme.scale(6);
        }

        private double handleHeight() {
            return height / actualHeight * height;
        }

        private double handleX() {
            return x + width - handleWidth();
        }

        private double handleY() {
            return y + (height - handleHeight()) * (scroll / (actualHeight - height));
        }

        private boolean isWidgetInView(WWidget widget) {
            return widget.y < y + height && widget.y + widget.height > y;
        }
    }
}
