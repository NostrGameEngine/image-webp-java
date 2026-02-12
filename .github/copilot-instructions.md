# Copilot instructions for `image-webp`

## Build / test / lint (Rust)

This crate targets **Rust 1.80.1+** (see `Cargo.toml` and CI).

- Build:
  ```bash
  cargo build
  ```

- Run all tests:
  ```bash
  cargo test
  ```

- Run a single integration test target (from `tests/`):
  ```bash
  cargo test --test decode
  ```

- Run a single test by name (works for unit + integration tests):
  ```bash
  cargo test reftest_gallery1_1
  # or, scoped to the integration test binary:
  cargo test --test decode reftest_gallery1_1
  ```

- Generate docs (CI runs this with tests on most toolchains):
  ```bash
  cargo doc
  ```

- Format:
  ```bash
  cargo fmt
  ```

- Format check (CI):
  ```bash
  cargo fmt -- --check
  ```

- Clippy (CI is strict and runs with all features):
  ```bash
  cargo clippy --all-features -- -D warnings
  ```

- Benchmarks (nightly only; gated by the internal feature used by CI):
  ```bash
  cargo +nightly bench --features _benchmarks
  ```

- Big-endian CI uses `cross` (optional local reproduction):
  ```bash
  cross test --target powerpc-unknown-linux-gnu --verbose -v
  ```

- Fuzzing targets live under `fuzz/` (requires `cargo-fuzz`):
  ```bash
  cd fuzz
  cargo fuzz run decode_still
  cargo fuzz run decode_animated
  ```

## High-level architecture

- **Public API surface**: `src/lib.rs` is the crate root and re-exports the main types:
  - Decoder: `WebPDecoder`, `WebPDecodeOptions`, `UpsamplingMethod`, plus `DecodingError`
  - Encoder: `WebPEncoder`, `EncoderParams`, `ColorType`, plus `EncodingError`

- **Container parsing and dispatch** (`src/decoder.rs`):
  - Parses the RIFF/WebP container, records chunk offsets in a `HashMap<WebPRiffChunk, Range<u64>>`, and classifies the file as:
    - `VP8` (lossy),
    - `VP8L` (lossless), or
    - `VP8X` “extended” (features like animation/metadata).
  - Decoding is delegated to:
    - `Vp8Decoder` (lossy VP8) from `src/vp8.rs`, and
    - `LosslessDecoder` (VP8L) from `src/lossless.rs`.
  - Extended/animated WebP uses helpers in `src/extended.rs` and composites frames onto a canvas (including disposal/blending).

- **Encoding** (`src/encoder.rs`):
  - Produces a RIFF/WebP container and writes either:
    - `VP8L` (lossless) bitstreams by default (predictor/subtract-green transforms + Huffman coding), or
    - `VP8 ` (lossy) when `EncoderParams.use_lossy` is enabled (implemented in `src/vp8_encoder.rs`).
  - For lossy images with alpha, the encoder writes an extended container (`VP8X`) and stores alpha as an `ALPH` chunk encoded using the lossless path.

## Key repo-specific conventions

- **No unsafe**: the crate has `#![forbid(unsafe_code)]` (and CI/README treat this as a project invariant).

- **Docs are enforced**: `#![deny(missing_docs)]` is set in `src/lib.rs`; new public items generally need rustdoc.

- **Extensible public enums/structs**: public error enums and option structs are marked `#[non_exhaustive]` (don’t write exhaustive matches downstream; prefer a wildcard arm).

- **Decoder readers should be buffered**: `WebPDecoder` is implemented for `R: BufRead + Seek` and performs many small reads; use `std::io::BufReader` around files/streams.

- **Memory limiting is explicit**: `WebPDecoder::set_memory_limit()` affects chunk reads (ICC/EXIF/XMP and other chunk loads).

- **Reference decoding tests are fixture-based**:
  - Inputs: `tests/images/*.webp`
  - Expected outputs: `tests/reference/*.png`
  - Test generator: `tests/decode.rs` uses `paste` to generate many `reftest_*` functions; use `cargo test <name>` to run one.
  - For debugging mismatches locally, toggle `WRITE_IMAGES_ON_FAILURE` in `tests/decode.rs` to dump `tests/out/*.png`.
