package io.github.imagewebp.decoder;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.PngReaderByte;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ReferenceFixturesTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "gallery2/1_webp_ll",
            "gallery2/2_webp_ll",
            "gallery2/3_webp_ll",
            "gallery2/4_webp_ll",
            "gallery2/5_webp_ll",
            "regression/color_index",
            "regression/lossless_indexed_1bit_palette",
            "regression/lossless_indexed_2bit_palette",
            "regression/lossless_indexed_4bit_palette",
    })
    void decodeLosslessMatchesReferencePng(String fixture) throws Exception {
        byte[] webp = Files.readAllBytes(Path.of(".", "tests", "images", fixture + ".webp"));
        DecodedWebP decoded = WebPDecoder.decode(webp);

        PngData ref = readPng(Path.of(".", "tests", "reference", fixture + ".png"));

        assertEquals(ref.width, decoded.width);
        assertEquals(ref.height, decoded.height);

        byte[] rgba = decoded.rgba;
        if (ref.channels == 4) {
            assertArrayEquals(ref.pixels, rgba);
        } else if (ref.channels == 3) {
            assertEquals(ref.width * ref.height * 3, ref.pixels.length);
            for (int i = 0, p3 = 0; i < rgba.length; i += 4) {
                assertEquals(ref.pixels[p3++], rgba[i]);
                assertEquals(ref.pixels[p3++], rgba[i + 1]);
                assertEquals(ref.pixels[p3++], rgba[i + 2]);
                assertEquals((byte) 0xFF, rgba[i + 3]);
            }
        } else {
            fail("Unexpected PNG channels: " + ref.channels);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gallery1/1",
            "gallery1/2",
    })
    void decodeLossyMatchesReferencePng(String fixture) throws Exception {
        byte[] webp = Files.readAllBytes(Path.of(".", "tests", "images", fixture + ".webp"));
        DecodedWebP decoded = WebPDecoder.decode(webp);
        PngData ref = readPng(Path.of(".", "tests", "reference", fixture + ".png"));

        assertEquals(ref.width, decoded.width);
        assertEquals(ref.height, decoded.height);

        byte[] rgba = decoded.rgba;
        if (ref.channels == 4) {
            assertArrayEquals(ref.pixels, rgba);
        } else {
            for (int i = 0, p3 = 0; i < rgba.length; i += 4) {
                assertEquals(ref.pixels[p3++], rgba[i]);
                assertEquals(ref.pixels[p3++], rgba[i + 1]);
                assertEquals(ref.pixels[p3++], rgba[i + 2]);
            }
        }
    }

    private static PngData readPng(Path path) throws IOException {
        PngReaderByte reader = new PngReaderByte(path.toFile());
        try {
            ImageInfo info = reader.imgInfo;
            assertEquals(8, info.bitDepth);
            int channels = info.channels;
            assertTrue(channels == 3 || channels == 4);

            byte[] pixels = new byte[info.cols * info.rows * channels];
            for (int y = 0; y < info.rows; y++) {
                ImageLineByte line = (ImageLineByte) reader.readRow(y);
                System.arraycopy(line.getScanlineByte(), 0, pixels, y * info.cols * channels, info.cols * channels);
            }
            return new PngData(info.cols, info.rows, channels, pixels);
        } finally {
            reader.end();
        }
    }

    private static final class PngData {
        final int width;
        final int height;
        final int channels;
        final byte[] pixels;

        PngData(int width, int height, int channels, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.channels = channels;
            this.pixels = pixels;
        }
    }
}
