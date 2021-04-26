#!/bin/bash
set -xe

# This script clones the git repos, cleans them up, and builds the package
echo "--Setting Up Sources"

# Setup the debian files
cd plugins
mkdir build-${project}
cd build-${project}

BUILD_TYPE=gem
# Import variables from the project, allowing it to override behaviour
if [ -e ../${project}/build_vars.sh ]; then
  . ../${project}/build_vars.sh
fi

VERSION=$(head -n1 ../${project}/debian/changelog|awk '{print $2}'|sed 's/(//;s/)//'|cut -f1 -d-|cut -d: -f2)
if [[ $BUILD_TYPE == "gem" ]]; then
  cp -r ../${project} ./
  cd ${project}
  ../../download_gems
elif [[ $BUILD_TYPE == "ansible-collection" ]];
  COLLECTION=${project#ansible-collection-}
  wget "https://galaxy.ansible.com/download/${COLLECTION}-${VERSION}.tar.gz"
  mv "${COLLECTION}-${VERSION}.tar.gz" "${project}_${VERSION}.orig.tar.gz"
  cp -r ../${project} ./
  tar -x -C "${project}" -f "${project}_${VERSION}.orig.tar.gz"
  cd ${project}
else
  echo "Unsupported build type: ${BUILD_TYPE}"
  exit 1
fi

# Add changelog entry if this is a git/nightly build
if [ x$gitrelease = xtrue ] || [ x$pr_number != x ]; then
  PACKAGE_NAME=$(head -n1 debian/changelog|awk '{print $1}')
  DATE=$(date -R)
  RELEASE="9999-plugin+scratchbuild+${BUILD_TIMESTAMP}"
  MAINTAINER="${repoowner} <no-reply@theforeman.org>"
  mv debian/changelog debian/changelog.tmp
  echo "$PACKAGE_NAME ($RELEASE) UNRELEASED; urgency=low

  * Automatically built package based on the state of
    foreman-packaging at commit $LAST_COMMIT

 -- $MAINTAINER  $DATE
" > debian/changelog

  cat debian/changelog.tmp >> debian/changelog
  rm -f debian/changelog.tmp
fi

# Build plugin
sudo pdebuild-${os}64

# Cleanup, pdebuild uses root
sudo chown -R jenkins:jenkins $WORKSPACE
