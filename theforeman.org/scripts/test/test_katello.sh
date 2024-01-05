#!/bin/bash -ex

TOP_ROOT=`pwd`
if [ -e $TOP_ROOT/foreman/Gemfile ]; then
  APP_ROOT=$TOP_ROOT/foreman
else
  APP_ROOT=$TOP_ROOT
fi
PLUGIN_ROOT=$TOP_ROOT/plugin

### Foreman PR testing ###
cd $APP_ROOT
if [ -n "${foreman_pr_git_url}" ]; then
  git remote add pr ${foreman_pr_git_url}
  git fetch pr
  git merge pr/${foreman_pr_git_ref}
fi

### PR testing ###
cd $PLUGIN_ROOT
if [ -n "${pr_git_url}" ]; then
  git remote add pr ${pr_git_url}
  git fetch pr
  git merge pr/${pr_git_ref}
fi

cd $APP_ROOT
mkdir config/settings.plugins.d

# setup basic settings file
cp $APP_ROOT/config/settings.yaml.example $APP_ROOT/config/settings.yaml

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

# Now let's introduce the plugin
echo "gemspec :path => '${PLUGIN_ROOT}', :development_group => :katello_dev" >> bundler.d/Gemfile.local.rb

# Install dependencies
bundle update --jobs=5 --retry=5

# Database environment
(
  sed "s/^test:/development:/; s/database:.*/database: ${gemset}-dev/" $HOME/postgresql.db.yaml
  echo
  sed "s/database:.*/database: ${gemset}-test/" $HOME/postgresql.db.yaml
) > $APP_ROOT/config/database.yml

# First try to drop the DB, but ignore failure as it might happen with Rails 5
# when there is really no DB yet.
bundle exec rake db:drop >/dev/null 2>/dev/null || true

# Create DB first in development as migrate behaviour can change
bundle exec rake db:create --trace
### END test_develop ###

# Now let's add the plugin migrations
bundle exec rake db:migrate --trace

# Katello-specific tests
bundle exec rake jenkins:katello TESTOPTS="-v" --trace

# Run the DB seeds to verify they work
bundle exec rake db:drop RAILS_ENV=test >/dev/null 2>/dev/null || true
bundle exec rake db:create RAILS_ENV=test
bundle exec rake db:migrate --trace RAILS_ENV=test
bundle exec rake db:seed --trace RAILS_ENV=test

# Clean up the database after use
bundle exec rake db:drop >/dev/null 2>/dev/null || true
