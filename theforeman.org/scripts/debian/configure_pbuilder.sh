#!/bin/bash
set -xe

# This script sets up the PBuilder hooks to pull in our deb repositories while the package is building
echo "--Apt Hook Setup"

pbuilder_hooksdir=/etc/pbuilder/${os}64/hooks
local_hooksdir=$(PWD)/hooks-${os}-${version}-${BUILD_ID}

# pbuilder doesn't accept multiple hookdirs, so we need to compose the real set of hooks
# from the static ones in /etc and the dynamic one we generate below
cp --recursive --preserve=mode ${pbuilder_hooksdir} ${local_hooksdir}

reposetup_hook = ${local_hooksdir}/F60addforemanrepo

echo "echo deb http://deb.theforeman.org/ ${os} ${version} >> /etc/apt/sources.list" > ${reposetup_hook}
echo "echo deb http://deb.theforeman.org/ plugins ${version} >> /etc/apt/sources.list" >> ${reposetup_hook}
echo "echo deb http://stagingdeb.theforeman.org/ ${os} theforeman-${version} >> /etc/apt/sources.list" >> ${reposetup_hook}

# Make executable
chmod 0755 ${reposetup_hook}
