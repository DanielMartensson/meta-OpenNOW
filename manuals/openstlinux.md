# meta-OpenNOW manual — OpenSTLinux integration (STM32 MPU)

Step-by-step guide for adding the OpenNOW cloud-gaming client to your own
OpenSTLinux distribution, from an empty host to a booting image.

Validated on the **STM32MP25** MPU family (aarch64, ARM Cortex-A35) with the
`openstlinux-weston` distro. Where you must adapt for other boards/BSPs the
text explicitly calls it out.

---

## 1. Host prerequisites

Standard OpenSTLinux host packages (Ubuntu 22.04/24.04/Debian/Mint: `expect`,
`gawk`, `git`, `python3`, ... — the list from ST's *Getting Started*) plus
roughly 100 GB free disk.

Nothing else. The Rust toolchain, Qt and the C/C++ cross toolchain are all
provided **inside** the Yocto build.

## 2. Unpack the ST source package

Download the ST source package for your MPU (e.g. *STM32MP2 Ecosystem Release,
Developer Package / Sources*) and unpack it. It contains `layers/`,
`setup.sh`, etc.:

```bash
cd <STM32MP2-source-tree>
source layers/meta-st/scripts/envsetup.sh --no-ui \
  build-openstlinuxweston-stm32mp25-<your-machine>
```

`--no-ui` skips the distribution/machine selector (use `DISTRO=... MACHINE=...`
env vars to pre-select them). This creates the
`build-openstlinuxweston-stm32mp25-<machine>/` build directory and sets up
`bblayers.conf` / `local.conf`.

## 3. Add this layer and its dependencies

Clone everything into the source tree's `layers/` directory:

```bash
cd layers
git clone https://github.com/<you>/meta-opennow.git meta-opennow
git clone --depth 1 -b 6.8        https://code.qt.io/yocto/meta-qt6.git meta-qt6
git clone --depth 1               https://github.com/rust-embedded/meta-rust-bin.git meta-rust-bin
git clone --depth 1 -b scarthgap  https://github.com/kraj/meta-clang.git meta-clang
# meta-openembedded (openembedded-layer, meta-python) ships with ST's package
# by default; add it only if it is missing from your tree.
# libsdl3 needed by opennow-qt: provide a recipe (not in stock ST tree) —
# the validating build used a libsdl3_3.4.14 from the BSP layer.
```

## 4. Register the layers

Edit `<builddir>/conf/bblayers.conf` and append:

```bash
BBLAYERS += " <tree>/layers/meta-opennow <tree>/layers/meta-qt6 <tree>/layers/meta-rust-bin <tree>/layers/meta-clang "
```

## 5. Required settings in local.conf

```bash
# opennow recipes require opengl, vulkan and wayland
DISTRO_FEATURES:append = " opengl vulkan wayland"
# ffmpeg is a DEPENDS of opennow-runtime (commercial FLOSS license flag)
LICENSE_FLAGS_ACCEPTED += "commercial"
# pull the client into the image
CORE_IMAGE_EXTRA_INSTALL:append = " opennow"

# optional but recommended for reproducible builds:
PREFERRED_VERSION_rust  = "1.98.1"
PREFERRED_VERSION_cargo = "1.98.1"
# optional on weak hosts (the validating environment built with -j1):
# BB_NUMBER_THREADS = "1"
# PARALLEL_MAKE = "-j 1"
```

## 6. Build

```bash
bitbake st-image-weston     # your ST image, or simply:
bitbake opennow-image       # minimal reference image with OpenNOW
```

Artifacts land in `tmp-glibc/deploy/images/<machine>/` and are flashed
exactly like a stock OpenSTLinux image (STM32CubeProgrammer, `flashlayout`,
`stm32mp-signing`, ...).

## 7. What you must change for *your* board

* **Machine type** — `COMPATIBLE_HOST = "(x86_64|aarch64).*-linux$"` allows
  aarch64 targets and the x86_64 native recipes. 32-bit SoCs (STM32MP13x,
  STM32MP15x, ARMv7) are **not** supported out of the box; extend the pattern
  (e.g. `arm`) in every recipe if you need them (untested).
* **Decode backend** — default `OPENNOW_RUST_FEATURES ??= "linux-ffmpeg"`
  (software/v4l2, no VAAPI on STM32MP2x). Boards **with** VAAPI set
  `OPENNOW_RUST_FEATURES = "linux-ffmpeg,linux-vaapi"` in
  `opennow-runtime.bb` and add `libva` to its `DEPENDS`.
* **GPU userland** — on STM32MP2x ensure the Vivante gcnano userland
  (`gcnano-userland-*`) is in your image so Qt/OpenGL/Vulkan can render.
* **Rust version** — meta-rust-bin ships several toolchains; pin with
  `PREFERRED_VERSION_rust`/`cargo`. OpenNOW requires >= 1.85 (validated 1.98.1).
* **OpenNOW revision / crates** — with an OpenNOW checkout:
  `scripts/update-sources.py --revision <commit>` regenerates
  `opennow-crates.inc`, `opennow-sdl.inc`, `opennow-source.inc`;
  `--check` (and `tests/test_sources.py`) verifies they are current.
* **Your own image** — copy `recipes-core/images/opennow-image.bb` and add
  `opennow` to `IMAGE_INSTALL`.

## 8. How the offline crate flow works

`opennow-crates.inc` lists every crate as `crate://crates.io/<name>/<ver>`
with a sha256. `do_fetch` — the only task allowed to touch the network —
downloads and verifies each. The crate fetcher unpacks them straight into
`${CARGO_HOME}/bitbake`; `classes/opennow-cargo.bbclass` writes a cargo
config that replaces `crates-io` with that directory and forces `--frozen`
offline resolution, so `do_compile` never touches the network. The SDL2 fork
(`zortos293/rust-sdl2`) is fetched as gitsm and vendored the same way. After
the first fetch, a shared `downloads/` directory (or git mirror) lets you
rebuild fully offline.

## 9. Runtime

Boot the built image and start the client:

```bash
opennow-qt
```

The image ships `opennow-qt`, `opennow-core`, `opennow-streamer`,
`opennow-update-helper`, `opennow-acceptance-verify`, the
`libopennow_streamer_ffi.so` library, plus the `.desktop` entry and icon.

## 10. Troubleshooting

* **`opennow-runtime:do_configure` fails with libva/libdrm pkg-config
  error** — the prebuilt-mode guard did not cut in; check
  `OPENNOW_PREBUILT_NATIVE_DIR` in `opennow-runtime.bb` and that the runtime
  was built first.
* **Cargo invoked from CMake during `opennow:do_configure/do_compile`** —
  prebuilt mode is off; re-check the
  `-DOPENNOW_PREBUILT_NATIVE_DIR=...` `EXTRA_OECMAKE` argument in
  `opennow.bb`.
* **`FFAPI` / `AVVulkanDeviceContext` compile errors** — building against
  FFmpeg 5 (`libavcodec.so.59`); the FFmpeg 6.x patches expect the STM32MP2
  FFmpeg 6.x tree.
* **Rust too old** — `cargo: error: no matching package found` / unstable
  feature errors: pin a newer `PREFERRED_VERSION_rust/cargo` (>= 1.85).
* **`commercial` not accepted** — ffmpeg refuses to build; add
  `LICENSE_FLAGS_ACCEPTED += "commercial"`.
* **Blank/black frame or Vulkan/QML errors** — check the gcnano GPU userland
  packagegroup and that `opengl`, `vulkan`, `wayland` distro features are on.
* **Qt lookup errors on target** — ensure `qtbase-plugins`,
  `qtdeclarative-qmlplugins`, `qtmultimedia-plugins/-qmlplugins`,
  `qtsvg-plugins` and `qtwayland-plugins` are in the image (they are
  `RDEPENDS` of the `opennow` package).
