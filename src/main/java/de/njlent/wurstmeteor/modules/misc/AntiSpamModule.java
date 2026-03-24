package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.mixin.ChatHudAccessor;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public class AntiSpamModule extends Module {
    public AntiSpamModule() {
        super(WurstMeteorAddon.CATEGORY, "anti-spam", "Merges duplicate chat messages and appends [xN].");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.inGameHud == null || mc.textRenderer == null) return;

        ChatHud chatHud = mc.inGameHud.getChatHud();
        List<ChatHudLine.Visible> chatLines = ((ChatHudAccessor) chatHud).getVisibleMessages();
        if (chatLines.isEmpty()) return;

        int maxTextLength = (int) Math.floor(ChatHud.getWidth(mc.options.getChatWidth().getValue()) / mc.options.getChatScale().getValue());
        List<OrderedText> newLines = mc.textRenderer.wrapLines(event.getMessage(), maxTextLength);
        if (newLines.isEmpty()) return;

        int spamCounter = 1;
        int matchingLines = 0;

        for (int i = chatLines.size() - 1; i >= 0; i--) {
            String oldLine = toPlainString(chatLines.get(i).content());

            if (matchingLines <= newLines.size() - 1) {
                String newLine = toPlainString(newLines.get(matchingLines));

                if (matchingLines < newLines.size() - 1) {
                    if (oldLine.equals(newLine)) matchingLines++;
                    else matchingLines = 0;

                    continue;
                }

                if (!oldLine.startsWith(newLine)) {
                    matchingLines = 0;
                    continue;
                }

                if (i > 0 && matchingLines == newLines.size() - 1) {
                    String nextOldLine = toPlainString(chatLines.get(i - 1).content());

                    String twoLines = oldLine + nextOldLine;
                    String addedText = safeSubstring(twoLines, newLine.length());

                    if (addedText.startsWith(" [x") && addedText.endsWith("]")) {
                        String oldSpamCounter = addedText.substring(3, addedText.length() - 1);

                        if (isInteger(oldSpamCounter)) {
                            spamCounter += Integer.parseInt(oldSpamCounter);
                            matchingLines++;
                            continue;
                        }
                    }
                }

                if (oldLine.length() == newLine.length()) {
                    spamCounter++;
                } else {
                    String addedText = safeSubstring(oldLine, newLine.length());

                    if (!addedText.startsWith(" [x") || !addedText.endsWith("]")) {
                        matchingLines = 0;
                        continue;
                    }

                    String oldSpamCounter = addedText.substring(3, addedText.length() - 1);
                    if (!isInteger(oldSpamCounter)) {
                        matchingLines = 0;
                        continue;
                    }

                    spamCounter += Integer.parseInt(oldSpamCounter);
                }
            }

            int end = Math.min(chatLines.size() - 1, i + matchingLines);
            for (int i2 = end; i2 >= i; i2--) chatLines.remove(i2);

            matchingLines = 0;
        }

        if (spamCounter > 1) {
            Text oldText = event.getMessage();
            MutableText newText = Text.empty().append(oldText.copy());
            event.setMessage(newText.append(" [x" + spamCounter + "]"));
        }
    }

    private static String toPlainString(OrderedText orderedText) {
        StringBuilder sb = new StringBuilder();
        orderedText.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });

        return sb.toString();
    }

    private static String safeSubstring(String text, int beginIndex) {
        if (beginIndex < 0 || beginIndex >= text.length()) return "";
        return text.substring(beginIndex);
    }

    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;

        int start = s.charAt(0) == '-' ? 1 : 0;
        if (start == s.length()) return false;

        for (int i = start; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }

        return true;
    }
}
