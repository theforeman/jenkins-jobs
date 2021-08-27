#!/bin/bash
set -xe

# This script sets up the PBuilder hooks to pull in our deb repositories while the package is building
echo "--Apt Hook Setup"

pbuilder_hooksdir=/etc/pbuilder/${os}64/hooks
addrepo_hook=${pbuilder_hooksdir}/F60-${project}-addforemanrepo

# Cleanup any old hooks
sudo rm -f $addrepo_hook

if [[ $repoowner != theforeman ]]; then
  # Add a stagingdeb repo to the sources too, if it exists
  if wget -O/dev/null -q http://stagingdeb.theforeman.org/dists/${os}/${repoowner}-${version} ; then
    echo "echo deb http://stagingdeb.theforeman.org/ ${os} ${repoowner}-${version} >> /etc/apt/sources.list" | sudo tee -a $addrepo_hook > /dev/null
  fi
  if wget -O/dev/null -q http://stagingdeb.theforeman.org/dists/plugins/${repoowner} ; then
    echo "echo deb http://stagingdeb.theforeman.org/ plugins ${repoowner} >> /etc/apt/sources.list" | sudo tee -a $addrepo_hook > /dev/null
  fi
fi

# Use tee to get around sudo/redirection problems, with /dev/null to drop the stdout
echo "echo deb http://deb.theforeman.org/ ${os} ${version} >> /etc/apt/sources.list" | sudo tee -a $addrepo_hook > /dev/null
echo "echo deb http://deb.theforeman.org/ plugins ${version} >> /etc/apt/sources.list" | sudo tee -a $addrepo_hook > /dev/null

# Read Foreman package installation logs on failure
echo "cat /var/log/foreman-install.log || true" | sudo tee ${pbuilder_hooksdir}/C10foremanlog

echo "tail -n 100 /etc/apt/sources.list /etc/apt/sources.list.d/*.list" | sudo tee ${pbuilder_hooksdir}/F99printrepos

# Make executable
sudo chmod 0775 ${pbuilder_hooksdir}/*
