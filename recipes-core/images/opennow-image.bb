SUMMARY = "Reference image with the OpenNOW Qt cloud gaming client"

inherit core-image

IMAGE_INSTALL:append = " opennow"

QB_MEM = "-m 4096"