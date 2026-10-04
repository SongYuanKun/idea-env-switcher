package io.github.ideaenvswitcher.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses dotenv assignments into literal environment values. */
public final class DotEnvParser {
    private static final int MAX_FILE_BYTES = 1024 * 1024;

    private DotEnvParser() { }

    public static Map<String, String> parse(String content) {
        Cursor cursor = new Cursor(content);
        Map<String, String> values = new LinkedHashMap<>();
        while (!cursor.end()) {
            cursor.spaces();
            if (cursor.end()) break;
            if (cursor.peek() == '#') cursor.comment();
            else if (cursor.newline()) cursor.take();
            else {
                int line = cursor.line;
                if (cursor.text.startsWith("export", cursor.index)
                        && cursor.index + 6 < content.length()
                        && horizontalSpace(content.charAt(cursor.index + 6))) {
                    for (int i = 0; i < 6; i++) cursor.take();
                    cursor.spaces();
                }
                int start = cursor.index;
                if (cursor.end() || !nameStart(cursor.peek())) throw invalid(line, "Invalid variable name.");
                cursor.take();
                while (!cursor.end() && (nameStart(cursor.peek()) || Character.isDigit(cursor.peek()) && cursor.peek() <= '9')) cursor.take();
                String key = content.substring(start, cursor.index);
                cursor.spaces();
                if (cursor.end() || cursor.take() != '=') throw invalid(line, "Expected a KEY=value assignment.");
                if (values.containsKey(key)) throw invalid(line, "Duplicate variable name.");
                cursor.spaces();
                String value;
                if (!cursor.end() && (cursor.peek() == '\'' || cursor.peek() == '"')) {
                    value = cursor.quotedValue();
                    cursor.spaces();
                    if (!cursor.end() && cursor.peek() != '#' && !cursor.newline()) {
                        throw invalid(cursor.line, "Unexpected text after the quoted value.");
                    }
                } else {
                    start = cursor.index;
                    while (!cursor.end() && cursor.peek() != '#' && !cursor.newline()) cursor.take();
                    value = content.substring(start, cursor.index).stripTrailing();
                }
                values.put(key, value);
            }
        }
        return values;
    }

    public static Map<String, String> parseFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Choose a regular UTF-8 .env file.");
        byte[] content;
        try (var stream = Files.newInputStream(file)) {
            content = stream.readNBytes(MAX_FILE_BYTES + 1);
        }
        if (content.length > MAX_FILE_BYTES) throw new IOException("The .env file must be 1 MiB or smaller.");
        try {
            return parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString());
        } catch (CharacterCodingException e) {
            throw new IOException("The .env file must use valid UTF-8 encoding.");
        }
    }

    private static boolean nameStart(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z' || value == '_';
    }

    private static boolean horizontalSpace(char value) { return value == ' ' || value == '\t'; }

    private static IllegalArgumentException invalid(int line, String reason) {
        // Diagnostics deliberately contain neither variable names nor values.
        return new IllegalArgumentException("Invalid .env at line " + line + ": " + reason);
    }

    private static final class Cursor {
        private final String text;
        private int index;
        private int line = 1;

        private Cursor(String text) {
            this.text = text;
            index = text.startsWith("\uFEFF") ? 1 : 0;
        }

        private boolean end() { return index == text.length(); }
        private char peek() { return text.charAt(index); }
        private boolean newline() { return peek() == '\r' || peek() == '\n'; }

        private char take() {
            char value = text.charAt(index++);
            if (value == '\0') throw invalid(line, "NUL characters are not allowed.");
            if (value == '\n' || value == '\r' && (end() || peek() != '\n')) line++;
            return value;
        }

        private void spaces() { while (!end() && horizontalSpace(peek())) take(); }
        private void comment() { while (!end() && !newline()) take(); }

        private String quotedValue() {
            int startLine = line;
            char quote = take();
            StringBuilder value = new StringBuilder();
            while (!end()) {
                char next = take();
                if (next == quote) return value.toString();
                if (quote == '"' && next == '\\' && !end()) {
                    char escaped = take();
                    switch (escaped) {
                        case '\\', '"' -> value.append(escaped);
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        default -> value.append('\\').append(escaped);
                    }
                } else value.append(next);
            }
            throw invalid(startLine, "Unclosed quoted value.");
        }
    }
}
