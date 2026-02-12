# image-webp-java

Pure-Java WebP **still image** decoder for **Java 11+**.
It is a mostly machine-translated port of the Rust library [image-rs/image-webp](https://github.com/image-rs/image-webp), with only limited manual review. As a result, this port may still contain bugs and decoding differences, including issues not present in the original library.


## Installation


Gradle (Groovy):

```groovy
dependencies {
  implementation 'org.ngengine:image-webp-decoder:<version>'
}
```


## Usage

The decoder returns tightly-packed **RGBA8888** data in a `ByteBuffer`.

```java
import org.ngengine.webp.decoder.DecodedWebP;
import org.ngengine.webp.decoder.WebPDecoder;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

byte[] webp = Files.readAllBytes(Path.of("image.webp"));

// Default allocator uses ByteBuffer.allocate(...)
DecodedWebP decoded = WebPDecoder.decode(webp);

// Or provide your own allocator (e.g. allocateDirect / engine allocator)
// DecodedWebP decoded = WebPDecoder.decode(webp, ByteBuffer::allocateDirect);

System.out.println(decoded.width + "x" + decoded.height + ", alpha=" + decoded.hasAlpha);
ByteBuffer rgba = decoded.rgba; // position=0, limit=width*height*4
```

## Development

- Run tests: `./gradlew test`
- Manual AWT viewer (test-scope helper): `./gradlew runAwtViewer -Pwebp=/path/to/file.webp`


## License

MIT

