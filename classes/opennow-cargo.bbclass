#
# OpenNOW cargo helper class
#
# Layers on top of meta-rust-bin's `cargo_bin` class to restore the
# reproducibility guarantees of the Yocto build model for the OpenNOW
# Rust workspaces:
#
#  * all crates are fetched and checksummed at do_fetch time via
#    `crate://crates.io/...` SRC_URI entries (do_fetch is the only task
#    allowed to touch the network);
#  * bitbake's crate fetcher unpacks every fetched crate directly into
#    ${CARGO_HOME}/bitbake/ together with a .cargo-checksum.json, i.e.
#    the exact layout cargo expects for a directory source;
#  * our do_configure writes a CARGO_HOME config that replaces
#    crates-io with that vendored directory source and forces offline
#    resolution;
#  * every cargo invocation goes through `--frozen` so cargo can never
#    reach the network during compile.
#
# Recipes that use this class must inherit `cargo_bin` as well (which
# provides the prebuilt rustc/cargo toolchain, the cross C/C++/linker
# wrappers and the task scaffolding) and must `require opennow-crates.inc`
# to pull in the crate:// sources.

CARGO_VENDORING_DIRECTORY ??= "${CARGO_HOME}/bitbake"

# Build (and artifacts) go to release profile
CARGO_BUILD_PROFILE = "release"

opennow_cargo_write_config() {
    mkdir -p "${CARGO_VENDORING_DIRECTORY}"
    cat > "${CARGO_HOME}/config.toml" <<EOF
[source.bitbake]
directory = "${CARGO_VENDORING_DIRECTORY}"

[source.crates-io]
replace-with = "bitbake"

[net]
offline = true
EOF
}

opennow_cargo_do_configure() {
    cargo_bin_do_configure
    opennow_cargo_write_config
}

# ffmpeg-sys-next's build script compiles a small host executable to probe
# the FFmpeg headers (FF_API_* deprecation macros) and RUNS it on the build
# host (it does not cross-run). Pointing it at the target sysroot would mix
# the host compiler with the target glibc, so feed it a shim tree that
# contains only the FFmpeg headers/libraries.
opennow_cargo_setup_ffmpeg() {
    local shim="${B}/ffmpeg-sysroot-shim"
    if [ -d "${shim}/include/libavcodec" ]; then
        export FFMPEG_DIR="${shim}"
        return
    fi
    mkdir -p "${shim}/include" "${shim}/lib/arm64"
    for d in libavcodec libavdevice libavfilter libavformat libavutil libswresample libswscale; do
        ln -sfn "${RECIPE_SYSROOT}/usr/include/${d}" "${shim}/include/${d}"
    done
    ln -sfn "${RECIPE_SYSROOT}/usr/lib/"libav*.so* "${shim}/lib/arm64/"
    export FFMPEG_DIR="${shim}"
}

# Reproduce the environment that cargo_bin_do_compile sets up so that
# recipes can run their own cargo invocations (multiple workspaces,
# multiple binaries) while keeping identical cross-compilation wiring.
opennow_cargo_export_env() {
    export TARGET_CC="${WRAPPER_DIR}/cc-wrapper.sh"
    export TARGET_CXX="${WRAPPER_DIR}/cxx-wrapper.sh"
    export CC="${WRAPPER_DIR}/cc-wrapper.sh"
    export CXX="${WRAPPER_DIR}/cxx-wrapper.sh"
    export BUILD_CC="${WRAPPER_DIR}/cc-native-wrapper.sh"
    export BUILD_CXX="${WRAPPER_DIR}/cxx-native-wrapper.sh"
    export HOST_CC="${WRAPPER_DIR}/cc-native-wrapper.sh"
    export HOST_CXX="${WRAPPER_DIR}/cxx-native-wrapper.sh"
    export TARGET_LD="${WRAPPER_DIR}/linker-wrapper.sh"
    export LD="${WRAPPER_DIR}/linker-wrapper.sh"
    export PKG_CONFIG_ALLOW_CROSS="1"
    export LDFLAGS=""
    export RUSTFLAGS="${RUSTFLAGS}"
    export CARGO_NET_OFFLINE="true"

    # This "DO_NOT_USE_THIS" option of cargo is currently the only way to
    # configure a different linker for host and target builds when
    # RUST_BUILD == RUST_TARGET.
    export __CARGO_TEST_CHANNEL_OVERRIDE_DO_NOT_USE_THIS="nightly"
    export CARGO_UNSTABLE_TARGET_APPLIES_TO_HOST="true"
    export CARGO_UNSTABLE_HOST_CONFIG="true"
    export CARGO_TARGET_APPLIES_TO_HOST="false"
    export CARGO_TARGET_${CARGO_TARGET_LINKER_NAME}_LINKER="${WRAPPER_DIR}/linker-wrapper.sh"
    export CARGO_HOST_LINKER="${WRAPPER_DIR}/linker-native-wrapper.sh"
    export CARGO_BUILD_FLAGS="-C rpath"
    export CARGO_PROFILE_RELEASE_DEBUG="true"

    opennow_cargo_setup_ffmpeg

    # The CC crate defaults to using CFLAGS when compiling everything. We can
    # give it custom flags for compiling on the host.
    export HOST_CXXFLAGS=""
    export HOST_CFLAGS=""
}

# Offline, reproducible, release cargo build against the TARGET.
opennow_cargo_build() {
    opennow_cargo_export_env
    bbnote "cargo build --frozen --release --target ${RUST_TARGET} $@"
    cargo build --frozen --release --target "${RUST_TARGET}" "$@"
}