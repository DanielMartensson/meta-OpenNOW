HOMEPAGE = "https://github.com/opencloudgaming/opennow"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=efa1357149ea0c1589fb5abd29da41b4"

SRC_URI = "git://github.com/opencloudgaming/opennow.git;protocol=https;nobranch=1"
SRCREV = "34afc8345504956ec4d5c6a5a3933bc7922e7599"

S = "${WORKDIR}/git"

OPENNOW_VERSION = "1.0.1"
PV = "${OPENNOW_VERSION}+git"
