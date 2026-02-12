package io.github.imagewebp.decoder;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SmokeTest {
    @Test
    void decoderRejectsInvalidInput() {
        assertThrows(WebPDecodeException.class, () -> WebPDecoder.decode(new byte[] {1,2,3}));
    }

    @Test
    void canReadFixtureBytes() throws Exception {
        Path p = Path.of(".", "tests", "images");
        assertTrue(Files.exists(p));
    }
}
