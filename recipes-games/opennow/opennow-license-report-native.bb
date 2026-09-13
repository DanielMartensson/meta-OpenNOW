SUMMARY = "Build-host license notice generator for OpenNOW"

require opennow-source.inc
require opennow-crates.inc

inherit cargo_bin pkgconfig native opennow-cargo

DEPENDS += "dbus-native"
export CARGO_PROFILE_RELEASE_STRIP = "false"

do_configure() {
    opennow_cargo_do_configure
}

do_compile() {
    opennow_cargo_build --manifest-path ${S}/native/opennow-core/Cargo.toml \
        --bin opennow-license-report
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${CARGO_BINDIR}/opennow-license-report ${D}${bindir}/
}