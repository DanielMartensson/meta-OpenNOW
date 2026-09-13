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

## OpenSTLinux integration manual

Step-by-step instructions for adding the OpenNOW client to your own
OpenSTLinux distribution (validated on the STMicroelectronics STM32MP25
MPU, `openstlinux-weston` distro, aarch64).

### 1. Host prerequisites

ST's standard build host packages (Ubuntu 22.04/24.04, Debian or Mint)
from the *OpenSTLinux Getting Started* list — `expect`, `gawk`, `git`,
`python3`, etc. — plus roughly 100 GB free disk. Rust, Qt and the C++
toolchain are all provided *inside* the Yocto build; nothing extra is
installed on the host.

### 2. Unpack the ST source package

Download the ST source package for your MPU (e.g. *STM32MP25 Ecosystem
Release*, "Developer Package / Sources") and unpack it. From here on all
commands run inside that tree (it contains `layers/`, `setup.sh`, etc.):

```bash
cd <STM32MP25-source-tree>        # contains layers/meta-st/scripts/envsetup.sh
source layers/meta-st/scripts/envsetup.sh --no-ui build-openstlinuxweston-stm32mp25-<board> < <(yes y)
```

### 3. Add this layer and its dependencies

Clone everything into the source tree's `layers/` directory:

```bash
cd layers
git clone https://github.com/<you>/meta-OpenNOW.git meta-opennow
git clone --depth 1 -b 6.8        https://code.qt.io/yocto/meta-qt6.git meta-qt6
git clone --depth 1               https://github.com/rust-embedded/meta-rust-bin.git meta-rust-bin
git clone --depth 1 -b scarthgap  https://github.com/kraj/meta-clang.git meta-clang
# meta-openembedded (openembedded-layer, meta-python) normally comes with ST's
# package; only add it if it is missing from your tree.
```

`opennow-core` also links the SDL2 Rust crates (`zortos293/rust-sdl2`)
and the Qt app links **libsdl3**; if your BSP provides `libsdl3` it is used
directly, otherwise provide it (the validating build used the
`meta-watermelon-wine` BSP layer for `libsdl3_3.4.14`).

### 4. Register the layers in bblayers.conf

Edit `<builddir>/conf/bblayers.conf` and append:

```bash
BBLAYERS += " <tree>/layers/meta-opennow <tree>/layers/meta-qt6 <tree>/layers/meta-rust-bin <tree>/layers/meta-clang "
```

### 5. Required settings in local.conf

```bash
# opennow recipes require opengl, vulkan and wayland
DISTRO_FEATURES:append = " opengl vulkan wayland"
# ffmpeg is a DEPENDS of opennow-runtime (commercial FLOSS license flag)
LICENSE_FLAGS_ACCEPTED += "commercial"
# pull the client into the image
CORE_IMAGE_EXTRA_INSTALL:append = " opennow"

# optional, recommended for repeatable builds:
PREFERRED_VERSION_rust = "1.98.1"
PREFERRED_VERSION_cargo = "1.98.1"
# optional, weak hosts only (the validating environment built with -j1):
# BB_NUMBER_THREADS = "1"
# PARALLEL_MAKE = "-j 1"
```

### 6. Build

```bash
bitbake st-image-weston      # your ST image, or simply:
bitbake opennow-image        # minimal reference image with OpenNOW
```

The rootfs/image artifacts land in
`tmp-glibc/deploy/images/<machine>/` and are flashed exactly like a stock
OpenSTLinux image (STM32CubeProgrammer, `flashlayout`, `stm32mp-signing`,
...).

### 7. What you must change for *your* board

* **Machine type** — `COMPATIBLE_HOST = "(x86_64|aarch64).*-linux$"` allows
  aarch64 targets and the x86_64 native recipes. 32-bit SoCs (STM32MP13x,
  STM32MP15x, ARMv7) are **not** supported out of the box; extend the
  pattern (e.g. `arm`) in every recipe if you need them (untested).
* **Decode backend** — default `OPENNOW_RUST_FEATURES ??= "linux-ffmpeg"`
  (software/v4l2, no VAAPI on STM32MP2x). Boards **with** VAAPI:
  `OPENNOW_RUST_FEATURES = "linux-ffmpeg,linux-vaapi"` in `opennow-runtime.bb`
  and add `libva` to its `DEPENDS`.
* **GPU userland** — on STM32MP2x ensure the Vivante gcnano userland
  (`gcnano-userland-*`) is in your image so Qt/OpenGL/Vulkan can render.
* **Rust version** — meta-rust-bin ships several toolchains; pin with
  `PREFERRED_VERSION`. OpenNOW requires >= 1.85 (validated with 1.98.1).
* **OpenNOW revision / crates** — 
  `scripts/update-sources.py --revision <commit>` regenerates
  `opennow-crates.inc` / `opennow-sdl.inc` / `opennow-source.inc`;
  `--check` (and `tests/test_sources.py`) verifies they are current.
* **Your own image** — copy `recipes-core/images/opennow-image.bb` and add
  `opennow` to `IMAGE_INSTALL`.

### 8. How the offline crate flow works

`opennow-crates.inc` lists every crate as `crate://crates.io/<name>/<ver>`
with a sha256 (do_fetch is the *only* task allowed to touch the network).
The fetcher unpacks them straight into `${CARGO_HOME}/bitbake` and
`classes/opennow-cargo.bbclass` writes a cargo config that replaces
`crates-io` with that directory and forces `--frozen` + offline resolution,
so `do_compile` never touches the network. A shared `downloads/` directory
(or git mirror) lets you rebuild fully offline afterwards.

### 9. Runtime

Boot the built image and start the client:

```bash
opennow-qt
```

The image ships `opennow-qt`, `opennow-core`, `opennow-streamer`,
`opennow-update-helper`, `opennow-acceptance-verify`,
`libopennow_streamer_ffi.so`, plus the `.desktop` entry and icon.

### 10. Troubleshooting

* `opennow:do_configure` fails with a `libva`/`libdrm` pkg-config error —
  the prebuilt-mode guard did not see `OPENNOW_PREBUILT_NATIVE_DIR`; make
  sure `opennow-runtime` built first and the dir exists in the recipe
  sysroot (`recipe-sysroot/usr/lib/opennow-native/`).
* `cargo: command not found` / Cargo invoked from CMake — prebuilt mode is
  off; re-check the `-DOPENNOW_PREBUILT_NATIVE_DIR=${STAGING_LIBDIR}/opennow-native`
  argument in `opennow.bb`.
* `FFAPI / AVVulkanDeviceContext` compile errors — you are building against
  FFmpeg 5 (`libavcodec.so.59`); the FFmpeg 6.x patches (0002/0003) expect
  the STM32MP2 FFmpeg 6.x tree.
* `commercial` license flag not accepted — ffmpeg will refuse to build;
  add `LICENSE_FLAGS_ACCEPTED += "commercial"`.
* Booting with black/blank frame or QML errors — check the gcnano GPU
  userland packagegroup and that `opengl`, `vulkan`, `wayland` distro
  features are enabled.
* Qt lookup errors on the target — ensure `qtbase-plugins`,
  `qtdeclarative-qmlplugins`, `qtmultimedia-plugins/-qmlplugins`,
  `qtsvg-plugins` and `qtwayland-plugins` are in the image (they are
  `RDEPENDS` of the `opennow` package).

## Adapting

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