#!/bin/bash
set -xe

# This script builds the package
echo "--Execute pdebuild"

# Build the package for the OS using pbuilder
# needs sudo as pedebuild uses loop and bind mounts
sudo REPO_SETUP=true REPO_VERSION=${version} pdebuild-${os}64

# Cleanup, pdebuild uses root
sudo chown -R jenkins:jenkins $WORKSPACE
