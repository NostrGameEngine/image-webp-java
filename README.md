# image-webp-decoder (Java)

Pure-Java WebP **still image** decoder for **Java 11+**.
It is a mostly machine-translated port of the Rust library [image-rs/image-webp](https://github.com/image-rs/image-webp) and then manually reviewed, so please report any decoding issues you find.

## Installation

### Maven Central

Gradle (Kotlin DSL):

```kotlin
dependencies {
  implementation("org.ngengine:image-webp-decoder:<version>")
}
```

Gradle (Groovy):

```groovy
dependencies {
  implementation 'org.ngengine:image-webp-decoder:<version>'
}
```

### GitHub Packages

```kotlin
repositories {
  maven {
    url = uri("https://maven.pkg.github.com/<OWNER>/<REPO>")
    credentials {
      username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
      password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }
}
```

## Usage

The decoder returns tightly-packed **RGBA8888** bytes.

```java
import io.github.imagewebp.decoder.DecodedWebP;
import io.github.imagewebp.decoder.WebPDecoder;

import java.nio.file.Files;
import java.nio.file.Path;

byte[] webp = Files.readAllBytes(Path.of("image.webp"));
DecodedWebP decoded = WebPDecoder.decode(webp);

System.out.println(decoded.width + "x" + decoded.height + ", alpha=" + decoded.hasAlpha);
byte[] rgba = decoded.rgba;
```

## Development

- Run tests: `./gradlew test`
- Manual AWT viewer (test-scope helper): `./gradlew runAwtViewer -Pwebp=/path/to/file.webp`

## Publishing (CI)

This repo includes GitHub Actions workflows that:
- build on every push
- optionally publish snapshots/releases to Maven Central when OSSRH + signing secrets are configured
- publish releases to GitHub Packages

## License

MIT

