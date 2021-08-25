#!/bin/bash
set -xe

# This script clones the git repos and cleans them up
echo "--Setting Up Sources"

# Setup the debian files, figure out the version
echo `git log -n1 --oneline`
cd debian/${os}/
VERSION=$(head -n1 ${project}/changelog|awk '{print $2}'|sed 's/(//;s/)//'|cut -f1 -d-)

# Setup sources
mkdir build-${project} && cd build-${project}
if [[ x$repo =~ ^xdevelop ]]; then
  url_base='https://ci.theforeman.org'
  job_name=$nightly_jenkins_job  # e.g. 'test_develop', from triggering job
  job_id=$nightly_jenkins_job_id # e.g. '123', from triggering or 'lastSuccessfulBuild'
  job_url="${url_base}/job/${job_name}/${job_id}"

  wget "${job_url}/artifact/*zip*/archive.zip"
  unzip archive.zip
  mv archive/pkg/*bz2 ${project}_${VERSION}.orig.tar.bz2

  # Set this in case we need it
  LAST_COMMIT=`curl "${job_url}/api/json" | jq -r '.actions[].lastBuiltRevision.SHA1 | values'`
else
  VERSION=`echo ${VERSION} | tr '~rc' '-rc'`
  # Download sources
  wget https://downloads.theforeman.org/${project}/${project}-${VERSION}.tar.bz2 https://downloads.theforeman.org/${project}/${project}-${VERSION}.tar.bz2.sig

  # Verify with packaging key - commented until we can handle multiple keys
#  tmp_keyring="./1AA043B8-keyring.gpg"
#  gpg --no-default-keyring --keyserver keys.gnupg.net --keyring $tmp_keyring --recv-keys 1AA043B8
#  if gpg --no-default-keyring --keyring $tmp_keyring ${project}-${VERSION}.tar.bz2.sig 2>&1 | grep -q "gpg: Good signature from \"Foreman Automatic Signing Key (2014) <packages@theforeman.org>\"" ; then
#    true # ok
#  else
#    exit 2
#  fi
  mv ${project}-${VERSION}.tar.bz2 ${project}_${VERSION}.orig.tar.bz2

  # Set this for test builds
  LAST_COMMIT=${VERSION}
fi

# Unpack
tar xvjf ${project}_${VERSION}.orig.tar.bz2
if [[ -d ${project}-${VERSION}-develop ]] ; then
	mv ${project}-${VERSION}-develop ${project}-${VERSION}
fi

# Bring in the debian packaging files
cp -r ../${project} ./${project}-${VERSION}/debian
cd ${project}-${VERSION}

# Add changelog entry if this is a git/nightly build
if [[ $gitrelease == true ]] || [[ -n $pr_number ]]; then
  PACKAGE_NAME=$(head -n1 debian/changelog|awk '{print $1}')
  DATE=$(date -R)
  RELEASE="9999-${os}+scratchbuild+${BUILD_TIMESTAMP}"
  MAINTAINER="${repoowner} <no-reply@theforeman.org>"
  mv debian/changelog debian/changelog.tmp
  echo "$PACKAGE_NAME ($RELEASE) UNRELEASED; urgency=low

  * Automatically built package based on the state of
    foreman-packaging at commit $LAST_COMMIT

 -- $MAINTAINER  $DATE
" > debian/changelog

  cat debian/changelog.tmp >> debian/changelog
  rm -f debian/changelog.tmp

  # rename orig tarball to stop lintian complaining
  mv ../${project}_${VERSION}.orig.tar.bz2 ../${project}_9999.orig.tar.bz2
fi
