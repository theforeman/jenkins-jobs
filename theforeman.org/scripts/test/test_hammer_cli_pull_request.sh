#!/bin/bash -ex

echo "Setting up RVM environment."
set +x
# RVM Ruby environment
. /etc/profile.d/rvm.sh
# Use a gemset unique to each executor to enable parallel builds
gemset=$(echo ${JOB_NAME} | cut -d/ -f1)-${EXECUTOR_NUMBER}
rvm use ruby-${ruby}@${gemset} --create
rvm gemset empty --force
set -x

if [ "${ruby}" = '2.7' ]
then
    gem install bundler -v 2.4.22 --no-document
else
    gem install bundler --no-document
fi

bundle install --without development --jobs=5 --retry=5
bundle exec rake ci:setup:minitest test TESTOPTS="-v"
