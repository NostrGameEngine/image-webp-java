package io.github.imagewebp.decoder;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Port of Rust unit tests in src/vp8_arithmetic_decoder.rs. */
final class Vp8ArithmeticDecoderTest {
    @Test
    void arithmeticDecoderHelloShort() throws Exception {
        Vp8ArithmeticDecoder dec = new Vp8ArithmeticDecoder();
        byte[] data = "hel".getBytes(StandardCharsets.US_ASCII);
        dec.init(data, data.length);

        assertFalse(dec.readFlag());
        assertTrue(dec.readBool(10));
        assertFalse(dec.readBool(250));
        assertEquals(1, dec.readLiteral(1));
        assertEquals(5, dec.readLiteral(3));
        assertEquals(64, dec.readLiteral(8));
        assertEquals(185, dec.readLiteral(8));
    }

    @Test
    void arithmeticDecoderHelloLong() throws Exception {
        Vp8ArithmeticDecoder dec = new Vp8ArithmeticDecoder();
        byte[] data = "hello world".getBytes(StandardCharsets.US_ASCII);
        dec.init(data, data.length);

        assertFalse(dec.readFlag());
        assertTrue(dec.readBool(10));
        assertFalse(dec.readBool(250));
        assertEquals(1, dec.readLiteral(1));
        assertEquals(5, dec.readLiteral(3));
        assertEquals(64, dec.readLiteral(8));
        assertEquals(185, dec.readLiteral(8));
        assertEquals(31, dec.readLiteral(8));
    }

    @Test
    void arithmeticDecoderUninitErrors() {
        Vp8ArithmeticDecoder dec = new Vp8ArithmeticDecoder();
        assertThrows(WebPDecodeException.class, dec::readFlag);
    }
}
