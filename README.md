# meta-opennow

Yocto/OpenEmbedded layer that builds the
[OpenNOW](https://github.com/OpenCloudGaming/OpenNOW) cloud-gaming client — a
Qt 6 Quick shell with a Rust native runtime — and packages it for embedded
Linux (aarch64 / x86_64 only).

OpenNOW is taken from upstream `v1.0.1`; no fork and no patches are applied.
All Rust crates (`opennow-crates.inc`) and the SDL2 fork (`opennow-sdl.inc`)
are pinned and fetched offline.

## Table of contents

1. [Dependencies](#dependencies)
2. [Distro features and license flags](#distro-features-and-license-flags)
3. [Machines](#machines)
4. [Recipes](#recipes)
5. [Usage](#usage)
6. [Development](#development)
7. [License and maintainer](#license-and-maintainer)

## Dependencies

This layer depends on the following layers:

| Layer             | Collection (branch)    | Notes |
|-------------------|------------------------|-------|
| openembedded-core | `core` (scarthgap)     | must match your distro |
| meta-openembedded | `openembedded-layer`, `meta-python` (scarthgap) | |
| meta-qt6          | `qt6-layer` (6.8)      | Qt 6.8.x |
| meta-rust-bin     | `rust-bin-layer`       | prebuilt rustc/cargo (`cargo_bin`), Rust >= 1.85 (validated 1.98.1) |
| meta-clang        | `clang-layer`          | bindgen needs `clang-native` |

Declared in `conf/layer.conf`:

```bitbake
LAYERDEPENDS_opennow = "core openembedded-layer meta-python qt6-layer rust-bin-layer clang-layer"
```

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
