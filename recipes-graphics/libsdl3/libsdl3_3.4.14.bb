SUMMARY = "Simple DirectMedia Layer"
DESCRIPTION = "Simple DirectMedia Layer is a cross-platform multimedia \
library designed to provide low level access to audio, keyboard, mouse, \
joystick, 3D hardware via OpenGL, and 2D video framebuffer."
HOMEPAGE = "http://www.libsdl.org"
BUGTRACKER = "http://bugzilla.libsdl.org/"

SECTION = "libs"

LICENSE = "BSD-2-Clause & Zlib"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=036a54229112040a743509a86b30c80c \
                    file://src/hidapi/LICENSE.txt;md5=7c3949a631240cb6c31c50f3eb696077 \
                    file://src/hidapi/LICENSE-bsd.txt;md5=b5fa085ce0926bb50d0621620a82361f \
                    file://src/video/yuv2rgb/LICENSE;md5=79f8f3418d91531e05f0fc94ca67e071 \
                    "

SRC_URI = "http://www.libsdl.org/release/SDL3-${PV}.tar.gz \
"

S = "${WORKDIR}/SDL3-${PV}"

SRC_URI[sha256sum] = "30d4aa2b3037718142b32dffd4e72f917ebb6cc5227150e7bb9c45efb2153aeb"

inherit cmake lib_package binconfig-disabled pkgconfig upstream-version-is-even

CVE_PRODUCT = "simple_directmedia_layer sdl"

# SDL only forwards CMAKE_REQUIRED_FLAGS to check_symbol_exists() on some
# modules, so _GNU_SOURCE must also be passed via CMAKE_REQUIRED_DEFINITIONS.
# Without it glibc extensions guarded by __USE_GNU (getresuid, memfd_create,
# ppoll, ...) are misdetected as missing and HAVE_GETRESUID unset is a hard
# build failure: SDL_gtk.c then defines a static fallback that collides with
# the declaration in <unistd.h>.
EXTRA_OECMAKE = " \
	-DSDL_OSS_DEFAULT=OFF \
	-DSDL_DISKAUDIO=OFF \
	-DSDL_DUMMYVIDEO=OFF \
	-DSDL_RPI=OFF \
	-DSDL_PTHREADS=ON \
	-DSDL_RPATH=OFF \
	-DSDL_SNDIO=OFF \
	-DSDL_X11_XDBE=OFF \
	-DSDL_X11_XFIXES=OFF \
	-DSDL_X11_XSCRNSAVER=OFF \
	-DSDL_X11_XSHAPE=OFF \
	-DCMAKE_REQUIRED_DEFINITIONS=-D_GNU_SOURCE=1 \
"

# opengl packageconfig factored out to make it easy for distros
# and BSP layers to pick either (desktop) opengl, gles2, or no GL
PACKAGECONFIG_GL ?= "${@bb.utils.filter('DISTRO_FEATURES', 'opengl', d)}"

PACKAGECONFIG:class-native = "${PACKAGECONFIG_GL}"
PACKAGECONFIG:class-nativesdk = "${PACKAGECONFIG_GL}"
PACKAGECONFIG ??= " \
	${PACKAGECONFIG_GL} \
	${@bb.utils.filter('DISTRO_FEATURES', 'alsa pulseaudio pipewire vulkan', d)} \
	${@bb.utils.contains('DISTRO_FEATURES', 'wayland', 'wayland gles2', '', d)} \
	${@bb.utils.contains("TUNE_FEATURES", "neon","arm-neon","",d)} \
	${@bb.utils.contains_any("DISTRO_FEATURES", "x11 wayland","","console-build",d)} \
"
PACKAGECONFIG[alsa]       = "-DSDL_ALSA=ON,-DSDL_ALSA=OFF,alsa-lib,"
PACKAGECONFIG[arm-neon]   = "-DSDL_ARMNEON=ON,-DSDL_ARMNEON=OFF"
PACKAGECONFIG[console-build] = "-DSDL_UNIX_CONSOLE_BUILD=ON"
PACKAGECONFIG[gles2]      = "-DSDL_OPENGLES=ON,-DSDL_OPENGLES=OFF,virtual/libgles2"
PACKAGECONFIG[jack]       = "-DSDL_JACK=ON,-DSDL_JACK=OFF,jack"
PACKAGECONFIG[libusb] = "-DSDL_HIDAPI_LIBUSB=ON,-DSDL_HIDAPI_LIBUSB=OFF,libusb1"
PACKAGECONFIG[opengl]     = "-DSDL_OPENGL=ON,-DSDL_OPENGL=OFF,virtual/egl"
PACKAGECONFIG[pipewire] = "-DSDL_PIPEWIRE_SHARED=ON,-DSDL_PIPEWIRE_SHARED=OFF,pipewire"
PACKAGECONFIG[pulseaudio] = "-DSDL_PULSEAUDIO=ON,-DSDL_PULSEAUDIO=OFF,pulseaudio"
PACKAGECONFIG[vulkan]    = "-DSDL_VULKAN=ON -DSDL_RENDER_VULKAN=ON,-DSDL_VULKAN=OFF -DSDL_RENDER_VULKAN=OFF"
PACKAGECONFIG[wayland]    = "-DSDL_WAYLAND=ON,-DSDL_WAYLAND=OFF,wayland-native wayland wayland-protocols libxkbcommon"

CFLAGS:append:class-native = " -DNO_SHARED_MEMORY"

FILES:${PN} += "${datadir}/licenses/SDL3/LICENSE.txt"

BBCLASSEXTEND = "native nativesdk"