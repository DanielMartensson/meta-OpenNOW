# meta-opennow

Yocto/OpenEmbedded layer that builds the
[OpenNOW](https://github.com/OpenCloudGaming/OpenNOW) cloud-gaming client
(Qt 6 Quick app + Rust native runtime) and packages it for embedded Linux.

The layer is a Scarthgap-port of upstream OpenNOW's Yocto work (PR
[#905](https://github.com/OpenCloudGaming/OpenNOW/pull/905), which targets the
`dev` branch era of the project). The upstream PR tracked Yocto Wrynose and
shipped Rust 1.94 / Qt 6.11 via meta-qt6 master; this layer re-targets
Scarthgap with Qt 6.8 (meta-qt6 `6.8` branch) and a prebuilt Rust toolchain
(meta-rust-bin; validated with Rust 1.98.1, OpenNOW requires >= 1.85) and
adapts the decode backend for boards without VAAPI (e.g. STM32MP25, which has
no VAAPI driver — it uses the ffmpeg backend instead). The OpenNOW revision
is pinned to the project's `main` branch (see below).

## Layer dependencies

| Layer              | Collection     | Branch / commit note                    |
|--------------------|----------------|-----------------------------------------|
| openembedded-core  | `core`         | Scarthgap (must match build distro)     |
| meta-openembedded  | `openembedded-layer`, `meta-python` | Scarthgap |
| meta-qt6           | `qt6-layer`    | `6.8` branch (Qt 6.8.4, scarthgap in LAYERSERIES_COMPAT) |
| meta-rust-bin      | `rust-bin-layer` | prebuilt rustc/cargo via `cargo_bin` class; Rust >= 1.85, validated with 1.98.1 |
| meta-clang         | `clang-layer`  | `scarthgap` branch (bindgen needs `clang-native`) |

`LAYERDEPENDS_opennow = "core openembedded-layer meta-python qt6-layer rust-bin-layer clang-layer"`

## Recipes

* `opennow-runtime` — Rust workspaces (`native/opennow-core`,
  `native/opennow-streamer`), cross-compiled offline with the pins in
  `opennow-crates.inc` / `opennow-sdl.inc`. Installs the native artifacts
  into `${libdir}/opennow-native` (also exported to the sysroot so the Qt
  recipe can consume them).
* `opennow` — Qt shell (`opennow-qt`), built with `qt6-cmake`, links the
  prebuilt runtime via `OPENNOW_PREBUILT_NATIVE_DIR`.
* `opennow-license-report-native` — build-host tool generating
  `THIRD_PARTY_NOTICES.generated`.
* `opennow-image` — reference core-image.
* To include OpenNOW in an existing (e.g. static ST) image, add `opennow`
  to `CORE_IMAGE_EXTRA_INSTALL`.

## Build integration

```bash
# layers/ (repo-manifest siblings)
git clone --depth 1 -b 6.8      https://code.qt.io/yocto/meta-qt6.git      meta-qt6
git clone --depth 1             https://github.com/rust-embedded/meta-rust-bin.git meta-rust-bin
git clone --depth 1 -b scarthgap https://github.com/kraj/meta-clang.git     meta-clang

# bblayers.conf — append these alongside the existing layers
BBLAYERS += " <path>/meta-opennow <path>/meta-qt6 <path>/meta-rust-bin <path>/meta-clang "

# local.conf
LICENSE_FLAGS_ACCEPTED += "commercial"   # ffmpeg is a DEPENDS of opennow-runtime
DISTRO_FEATURES:append = " opengl vulkan wayland"
CORE_IMAGE_EXTRA_INSTALL:append = " opennow"
```

Then:

```bash
bitbake opennow-image        # or your own image with `opennow` installed
```

## Adapting

* **Decode backend** — `OPENNOW_RUST_FEATURES ??= "linux-ffmpeg"` in
  `opennow-runtime.bb`. Boards with VAAPI can set
  `OPENNOW_RUST_FEATURES = "linux-ffmpeg,linux-vaapi"` (add `libva` to
  DEPENDS then). STM32MP25 stays on `linux-ffmpeg`.
* **Rust toolchain** — meta-rust-bin provides several versions; bump by
  setting `PREFERRED_VERSION_rust` / `PREFERRED_VERSION_cargo` in `local.conf`
  (or adding `rust-bin-cross_<ver>.bb` / `cargo-bin-cross_<ver>.bb`).
  OpenNOW requires Rust >= 1.85; validated with 1.98.1.
* **OpenNOW revision / crates** — with an OpenNOW checkout:
  `scripts/update-sources.py --revision <commit>` regenerates
  `opennow-crates.inc`, `opennow-sdl.inc`, `opennow-source.inc`;
  `--check` (and `tests/test_sources.py`) verifies they are current.

## Validated build

Successfully built (all 11798 tasks, no errors) as part of a full
`st-image-weston` image for the `watermelon-wine-1a` machine (STM32MP25,
aarch64/ARM Cortex-A35, OpenSTLinux Weston distro):

| Component | Version |
|-----------|---------|
| OpenNOW    | `c1ca719` (1.0.0 release line, `main` branch) |
| Qt        | 6.8.x via meta-qt6 `6.8` branch |
| Rust      | 1.98.1 (meta-rust-bin `cargo_bin`/`rust_bin`) |
| FFmpeg    | 6.x from the ST layer (decode backend: `linux-ffmpeg`, no VAAPI on mp25) |

The resulting rootfs ships `opennow-qt`, `opennow-core`,
`opennow-streamer`, `opennow-update-helper`, `opennow-acceptance-verify`,
`libopennow_streamer_ffi.so`, the `.desktop` entry and icon.

## Offline / reproducibility notes

All crates are pinned `crate://crates.io/...` with sha256 in
`opennow-crates.inc`; bitbake's crate fetcher unpacks them straight into
`${CARGO_HOME}/bitbake`, and `classes/opennow-cargo.bbclass` configures
cargo with a crates-io → local directory replacement plus `--frozen`, so
`do_compile` never touches the network. The SDL2 fork (sdl2/sdl2-sys
0.38.0, `zortos293/rust-sdl2` @ `8c812c4`) is fetched gitsm and vendored
the same way.

OpenNOW itself is pinned to the OpenNOW `main` branch HEAD
(`c1ca71929249788bdef799202eb94660a7799ac2`, Qt 1.0.0 release line, MIT).
The crates/SDL2 pins were regenerated from that revision's committed
`Cargo.lock` files and verified with `update-sources.py --check`.