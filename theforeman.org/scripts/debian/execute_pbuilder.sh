#!/bin/bash
set -xe

# This script builds the package
echo "--Execute pdebuild"

# Build the package for the OS using pbuilder
# pbuilder calls sudo internally, so no sudo here
FOREMAN_VERSION=${version} pdebuild-${os}64

# Cleanup, pdebuild uses root
sudo chown -R jenkins:jenkins $WORKSPACE
