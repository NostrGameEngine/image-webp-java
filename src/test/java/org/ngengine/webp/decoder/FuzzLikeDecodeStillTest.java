package org.ngengine.webp.decoder;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fuzz-like robustness tests inspired by fuzz/fuzz_targets/decode_still.rs.
 *
 * The goal is not to assert decoding success, but that we don't hang or crash on arbitrary inputs.
 */
final class FuzzLikeDecodeStillTest {
    @Test
    void randomBytesDoNotCrash() {
        Random rnd = new Random(0xC0FFEE);
        for (int i = 0; i < 500; i++) {
            byte[] data = new byte[rnd.nextInt(2048)];
            rnd.nextBytes(data);

            try {
                WebPDecoder.decode(data);
            } catch (WebPDecodeException expected) {
                // ok
            } catch (OutOfMemoryError oom) {
                fail("OOM while decoding random bytes (should fail fast): " + oom);
            }
        }
    }

    @Test
    void syntheticVp8ContainersDoNotCrash() {
        Random rnd = new Random(0xBADC0DE);
        for (int i = 0; i < 200; i++) {
            int w = 1 + rnd.nextInt(64);
            int h = 1 + rnd.nextInt(64);

            // Minimal VP8 keyframe header with empty first partition.
            // Decoder will likely error later (empty/invalid bitstream), which is fine for this test.
            byte[] vp8Payload = new byte[10];
            int tag = 1 << 4; // show_frame=1, version=0, keyframe=0, first_partition_size=0
            vp8Payload[0] = (byte) (tag & 0xFF);
            vp8Payload[1] = (byte) ((tag >>> 8) & 0xFF);
            vp8Payload[2] = (byte) ((tag >>> 16) & 0xFF);
            vp8Payload[3] = (byte) 0x9D;
            vp8Payload[4] = 0x01;
            vp8Payload[5] = 0x2A;
            vp8Payload[6] = (byte) (w & 0xFF);
            vp8Payload[7] = (byte) ((w >>> 8) & 0xFF);
            vp8Payload[8] = (byte) (h & 0xFF);
            vp8Payload[9] = (byte) ((h >>> 8) & 0xFF);

            byte[] webp = wrapRiffWebpVp8(vp8Payload);

            try {
                DecodedWebP decoded = WebPDecoder.decode(webp);
                assertEquals(w, decoded.width);
                assertEquals(h, decoded.height);
                assertEquals(w * h * 4, decoded.rgba.remaining());
            } catch (WebPDecodeException expected) {
                // ok
            } catch (OutOfMemoryError oom) {
                fail("OOM while decoding synthetic small VP8 container: " + oom);
            }
        }
    }

    private static byte[] wrapRiffWebpVp8(byte[] vp8Payload) {
        int pad = vp8Payload.length & 1;
        int riffSize = 4 /*WEBP*/ + 8 /*chunk hdr*/ + vp8Payload.length + pad;

        ByteBuffer bb = ByteBuffer.allocate(8 + riffSize).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        bb.putInt(riffSize);
        bb.put((byte) 'W').put((byte) 'E').put((byte) 'B').put((byte) 'P');
        bb.put((byte) 'V').put((byte) 'P').put((byte) '8').put((byte) ' ');
        bb.putInt(vp8Payload.length);
        bb.put(vp8Payload);
        if (pad != 0) {
            bb.put((byte) 0);
        }
        return bb.array();
    }
}
