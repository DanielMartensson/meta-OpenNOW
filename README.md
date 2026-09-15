# meta-opennow

Yocto/OpenEmbedded layer that builds the
[OpenNOW](https://github.com/OpenCloudGaming/OpenNOW) cloud-gaming client — a
Qt 6 Quick shell with a Rust native runtime — and packages it for embedded
Linux (aarch64 / x86_64 only).

## Table of contents

1. [Dependencies](#dependencies)
2. [Distro features and license flags](#distro-features-and-license-flags)
3. [Machines](#machines)
4. [Recipes](#recipes)
5. [Usage](#usage)
6. [Development](#development)
7. [License and maintainer](#license-and-maintainer)

## Dependencies

This is the complete, minimal set — every entry is required:

| Layer             | Collection (branch)      | Why it is needed |
|-------------------|--------------------------|------------------|
| openembedded-core | `core` (scarthgap)       | base recipes and classes |
| meta-openembedded | `openembedded-layer`, `meta-python` (scarthgap) | required by **meta-qt6** |
| meta-qt6          | `qt6-layer` (6.8)        | `qt6-cmake` + Qt 6.8 modules (`qtdeclarative`, `qtmultimedia`, ...) |
| meta-rust-bin     | `rust-bin-layer`         | `cargo_bin` prebuilt rustc/cargo for the Rust runtime (>= 1.85) |
| meta-clang        | `clang-layer`            | `clang-native`, used by bindgen at build time |

Declared in `conf/layer.conf`:

```bitbake
LAYERDEPENDS_opennow = "core openembedded-layer meta-python qt6-layer rust-bin-layer clang-layer"
```

Notes:

* `openembedded-layer`/`meta-python` are not optional extras — **meta-qt6**
  itself declares `LAYERDEPENDS_qt6-layer = "core openembedded-layer meta-python"`,
  so they are pulled in transitively even before this layer is considered.
* This layer ships its own minimal `libsdl3` recipe
  (`recipes-graphics/libsdl3`) because OpenNOW links SDL3 for gamepad input,
  yet the `libsdl3` recipe only exists on current meta-openembedded master.
  It is Wayland-only and patch-free: `check_symbol_exists()` misses glibc
  extensions (notably `getresuid`) unless `_GNU_SOURCE` is passed via
  `CMAKE_REQUIRED_DEFINITIONS`, which the recipe's `EXTRA_OECMAKE` does.
* No other packages or layers are needed; Qt6 does the rendering
  (Vulkan/OpenGL ES), SDL3 only handles gamepad input.

## Distro features and license flags

The recipes `inherit features_check` and expect:

```bitbake
DISTRO_FEATURES:append = " opengl vulkan wayland"
```

`opennow-runtime` also `DEPENDS` on `ffmpeg`, so accept the `commercial`
license class flag:

```bitbake
LICENSE_FLAGS_ACCEPTED += "commercial"
```

## Machines

`COMPATIBLE_HOST` allows **aarch64** and **x86_64** Linux targets only.
32-bit SoCs (ARMv7, e.g. STM32MP13x/15x) are not supported out of the box.

* No VAAPI / no dedicated GPU decode (e.g. STM32MP25): stays on the default
  `linux-ffmpeg` backend.
* VAAPI-capable (Intel / AMD GPU, aarch64 + `libva`): enable
  `linux-vaapi` (`OPENNOW_RUST_FEATURES` — see `manuals/openstlinux.md`).

## Recipes

| Recipe                        | What it builds / installs |
|-------------------------------|---------------------------|
| `opennow-runtime`             | `opennow-core` + `opennow-streamer` (Rust, offline); installs the ffmpeg-FFI static/shared libs into `${libdir}/opennow-native` |
| `opennow-license-report-native` | host tool that generates `THIRD_PARTY_NOTICES.generated` |
| `opennow`                     | the Qt shell (`opennow-qt`) that links the prebuilt runtime |
| `libsdl3`                     | minimal Wayland-only SDL 3.4.14 build (`recipes-graphics/libsdl3`); OpenNOW links it for gamepad input |
| `opennow-image`               | minimal reference image |

## Usage

Add this layer and its dependencies to `bblayers.conf`, enable the distro
features and license flags above, then add `opennow` to an image:

```bitbake
CORE_IMAGE_EXTRA_INSTALL:append = " opennow"
```

or build the bundled reference image:

```bash
bitbake opennow-image
```

Step-by-step guides:

* [OpenSTLinux / STM32MP2x integration](manuals/openstlinux.md)
* [Updating the OpenNOW revision and crate pins](manuals/update-sources.md)

## Development

After changing the pinned OpenNOW revision (or its `Cargo.lock`), regenerate
the pins and verify them:

```bash
python3 scripts/update-sources.py --revision <commit>   # regenerate pins
python3 scripts/update-sources.py --check               # verify pins are current
python3 tests/test_sources.py
```

Both check modes fail (non-zero exit) if the layer's pins drift from the
pinned revision.

## License and maintainer

MIT — see [LICENSE](LICENSE).

Maintained by [Daniel Martensson](https://github.com/DanielMartensson).
