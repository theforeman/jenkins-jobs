#!/bin/bash
set -xe

if [[ $gitrelease == true ]] || [[ -n $pr_number ]]; then

  echo "--Adding changelog entry"

  PACKAGE_NAME=$(head -n1 debian/changelog|awk '{print $1}')
  DATE=$(date -R)
  RELEASE="9999-${VERSION}-${suite}+scratchbuild+${BUILD_TIMESTAMP}"
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
