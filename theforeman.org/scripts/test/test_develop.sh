#!/bin/bash -ex

APP_ROOT=`pwd`

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


# Retry as rubygems (being external to us) can be intermittent
bundle install --without=development --jobs=5 --retry=5

# Rubocop
bundle exec rake rubocop

# Database environment
(
  sed "s/^test:/development:/; s/database:.*/database: ${gemset}-dev/" $HOME/postgresql.db.yaml
  echo
  sed "s/^test:/production:/; s/database:.*/database: ${gemset}-prod/" $HOME/postgresql.db.yaml
  echo
  sed "s/database:.*/database: ${gemset}-test/" $HOME/postgresql.db.yaml
) > $APP_ROOT/config/database.yml

# we need to install node modules for integration tests
npm install --no-audit

# Create DB first in development as migrate behaviour can change
bundle exec rake db:drop >/dev/null 2>/dev/null || true
bundle exec rake db:create db:migrate --trace

bundle exec rake pkg:generate_source jenkins:unit jenkins:integration TESTOPTS="-v" --trace

# Test asset precompile
rm -f db/schema.rb
cp db/schema.rb.nulldb db/schema.rb
bundle exec rake assets:precompile RAILS_ENV=production DATABASE_URL=nulldb://nohost
bundle exec rake webpack:compile RAILS_ENV=production DATABASE_URL=nulldb://nohost
