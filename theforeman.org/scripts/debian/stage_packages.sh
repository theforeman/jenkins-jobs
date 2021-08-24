#!/bin/bash
set -xeu

# Script to copy the newly-built debs to the web node for signing and promoting
# Dependencies
# * the freight::client class in foreman-infra
# * the web node must have the freight class applied

# Find the deps in the build-dir from the previous step
for possible_path in ./debian/${os}/build-${project} ./plugins/build-${project} ./dependencies/${os}/build-${project}; do
  if [[ -d "${possible_path}" ]]; then
    DEB_PATH="${possible_path}"
  fi
done

if [[ -z ${DEB_PATH:-} ]] ; then
  echo "No build-dir path found"
  exit 1
fi

# Upload all builds to stagingdeb for testing
HOSTS="web01.osuosl.theforeman.org"
export RSYNC_RSH="ssh -i /home/jenkins/workspace/staging_key/rsync_freightstage_key"
USER=freightstage
SUITE=${os}

if [[ "$DEB_PATH" == *"plugins"* ]];then
  SUITE="plugins"

  # Plugins aren't staged.
  # Builds from the main (theforeman) repo are uploaded directly to deb.tfm.o.
  # Only user (PR) builds are uploaded to stagingdeb.tfm.o.
  if [[ $repoowner == theforeman ]] && [[ -z ${pr_number:-} ]]; then
    echo "Built from main repo, uploading to deb/plugins/main"
    export RSYNC_RSH="ssh -i /home/jenkins/workspace/deb_key/rsync_freight_key"
    USER=freight
    if [[ $repo == develop ]]; then
      COMPONENT=nightly
    else
      COMPONENT=$repo
    fi
  else
    COMPONENT=${repoowner}
    echo "scratch build: uploading to stagingdeb/${SUITE}/${COMPONENT}"
  fi
else
  COMPONENT=${repoowner}-${version}
  echo "scratch build: uploading to stagingdeb/${SUITE}/${COMPONENT}"
fi

for HOST in $HOSTS; do
  # The path is important, as freight_rsync (which is run on the web node for incoming
  # transfers) will parse the path to figure out the repo to send debs to.
  TARGET_PATH="${USER}@${HOST}:rsync_cache/${SUITE}/${COMPONENT}/"

  if ls $DEB_PATH/*deb >/dev/null 2>&1; then
    /usr/bin/rsync -avPx $DEB_PATH/*deb $TARGET_PATH
  fi
done
