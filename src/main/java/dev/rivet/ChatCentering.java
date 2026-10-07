package dev.rivet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class ChatCentering {
    private static final int CENTER_PIXEL = 154;
    private static final int SPACE_WIDTH = 4;

    private ChatCentering() {
    }

    static Component center(Component message) {
        int spaces = centeringSpaces(PlainTextComponentSerializer.plainText().serialize(message));
        return spaces <= 0 ? message : Component.text(" ".repeat(spaces)).append(message);
    }

    static int centeringSpaces(String plainText) {
        int toCompensate = CENTER_PIXEL - pixelWidth(plainText) / 2;
        return toCompensate <= 0 ? 0 : toCompensate / SPACE_WIDTH;
    }

    static int pixelWidth(String text) {
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            width += glyphWidth(text.charAt(index)) + 1;
        }
        return width;
    }

    private static int glyphWidth(char character) {
        return switch (character) {
            case 'i', 'l', ':', ';', '.', ',', '\'', '!', '|' -> 1;
            case '`' -> 2;
            case ' ', 'I', '[', ']' -> 3;
            case 'f', 'k', 't', '(', ')', '{', '}', '<', '>' -> 4;
            case '@', '~' -> 6;
            default -> 5;
        };
    }
}
