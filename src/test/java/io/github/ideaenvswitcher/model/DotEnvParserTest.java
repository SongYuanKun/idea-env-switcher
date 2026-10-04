package io.github.ideaenvswitcher.model;

import io.github.ideaenvswitcher.service.DotEnvWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotEnvParserTest {
    @TempDir Path directory;

    @Test void importsCommonAssignmentsInSourceOrder() {
        String source = "\uFEFF# configuration\r\n\r\n export APP_MODE = dev # comment\r\n"
                + "URL=https://example.invalid/a=b?x=y\r\nEMPTY=\r\n"
                + "SPACED='  hello # world  '\r\nexport=literal\r\n";
        Map<String, String> values = DotEnvParser.parse(source);
        assertEquals(List.of("APP_MODE", "URL", "EMPTY", "SPACED", "export"), new ArrayList<>(values.keySet()));
        assertEquals(Map.of("APP_MODE", "dev", "URL", "https://example.invalid/a=b?x=y", "EMPTY", "",
                "SPACED", "  hello # world  ", "export", "literal"), values);
    }

    @Test void decodesDoubleQuotesAndPreservesLiteralSingleQuotesAndMultilineValues() {
        var values = DotEnvParser.parse("DOUBLE=\"say \\\"hi\\\" in C:\\\\tmp\\nnext\\tend\" # note\n"
                + "SINGLE='C:\\tmp\\n${HOME}'\nMULTI=\"first\r\nsecond\"\nAFTER=ok\n");
        assertEquals("say \"hi\" in C:\\tmp\nnext\tend", values.get("DOUBLE"));
        assertEquals("C:\\tmp\\n${HOME}", values.get("SINGLE"));
        assertEquals("first\r\nsecond", values.get("MULTI"));
        assertEquals("ok", values.get("AFTER"));
    }

    @Test void preservesReferencesAndCommandsAsText() {
        assertEquals(Map.of("REF", "${HOST}/$PATH", "COMMAND", "$(whoami)", "TICKS", "`hostname`",
                        "SLASH", "C:\\work", "UNKNOWN", "\\q"),
                DotEnvParser.parse("REF=${HOST}/$PATH\nCOMMAND=$(whoami)\nTICKS=`hostname`\nSLASH=C:\\work\nUNKNOWN=\"\\q\""));
    }

    @Test void rejectsInvalidAssignmentsWithLineNumbersWithoutExposingValues() {
        for (String bad : List.of("BROKEN", "BAD-KEY=private-fixture", "2KEY=private-fixture",
                "KEY=\"private-fixture", "KEY='private-fixture", "KEY=\"private-fixture\" trailing",
                "KEY=private-fixture\0", "GOOD=private-fixture")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> DotEnvParser.parse("GOOD=first\r\n" + bad));
            assertTrue(error.getMessage().contains("line 2"), error.getMessage());
            assertFalse(error.getMessage().contains("private-fixture"));
        }
    }

    @Test void roundTripsFilesGeneratedByThePluginWithoutChangingValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("EMPTY", "");
        values.put("PATH", "C:\\work\\bin");
        values.put("TEXT", "say \"hello\" in C:\\my folder");
        values.put("HASH", "red#blue");
        values.put("APOSTROPHE", "it's ready");
        values.put("MULTI", "first\nsecond\r\nthird\rfourth");
        values.put("UNICODE", "测试环境");
        values.put("LITERAL", "${HOST}/$(command)");
        String generated = DotEnvWriter.toDotEnvContent(new EnvProfile("test", null, values));
        assertEquals(values, DotEnvParser.parse(generated));
    }

    @Test void readsUtf8FilesAndRejectsInvalidEncodingOversizedFilesAndDirectories() throws Exception {
        Path file = directory.resolve(".env.local");
        Files.writeString(file, "LABEL=测试\n");
        assertEquals(Map.of("LABEL", "测试"), DotEnvParser.parseFile(file));
        Files.write(file, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> DotEnvParser.parseFile(file));
        Files.write(file, new byte[1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> DotEnvParser.parseFile(file));
        assertThrows(IOException.class, () -> DotEnvParser.parseFile(directory));
    }
}
