HOMEPAGE = "https://github.com/opencloudgaming/opennow"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=efa1357149ea0c1589fb5abd29da41b4"

SRC_URI = "git://github.com/opencloudgaming/opennow.git;protocol=https;nobranch=1"
SRCREV = "34afc8345504956ec4d5c6a5a3933bc7922e7599"

# opennow-streamer-platform-linux's FFmpeg Vulkan backend is written against the
# bundled FFmpeg headers; the distribution FFmpeg (6.x) reworked
# AVVulkanDeviceContext/AVVkFrame so several modules do not compile against the
# system headers. On STM32MP25 the Vulkan video path is never exercised, so the
# affected expressions only need to type-check. See the patch headers.
SRC_URI += "file://0002-streamer-ffmpeg6-queue-families.patch"
SRC_URI += "file://0003-streamer-vulkan-device-ffmpeg6.patch"
SRC_URI += "file://0004-opennow-qt-prebuilt-rust.patch"
SRC_URI += "file://0005-opennow-qt-vulkan-extension-filter.patch"

S = "${WORKDIR}/git"

OPENNOW_VERSION = "1.0.1"
PV = "${OPENNOW_VERSION}+git"
