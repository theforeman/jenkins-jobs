#!/bin/bash -ex

cleanup() {
  bundle exec rake db:drop DISABLE_DATABASE_ENVIRONMENT_CHECK=true >/dev/null 2>/dev/null || true
  bundle exec rake db:drop RAILS_ENV=production DISABLE_DATABASE_ENVIRONMENT_CHECK=true >/dev/null 2>/dev/null || true
}

trap cleanup SIGINT SIGTERM ERR EXIT

APP_ROOT=`pwd`

# setup basic settings file
cp $APP_ROOT/config/settings.yaml.example $APP_ROOT/config/settings.yaml

echo "Setting up rbenv environment."
export PATH="$HOME/.rbenv/shims:$PATH"
export RBENV_VERSION=${ruby}
export jobid=$(echo ${JOB_NAME} | cut -d/ -f1)-${EXECUTOR_NUMBER}

if [ "${ruby}" = '2.7.6' ]
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
  sed "s/^test:/development:/; s/database:.*/database: ${jobid}-dev/" $HOME/postgresql.db.yaml
  echo
  sed "s/^test:/production:/; s/database:.*/database: ${jobid}-prod/" $HOME/postgresql.db.yaml
  echo
  sed "s/database:.*/database: ${jobid}-test/" $HOME/postgresql.db.yaml
) > $APP_ROOT/config/database.yml

# we need to install node modules for integration tests
npm install --no-audit --legacy-peer-deps

# Create DB first in development as migrate behaviour can change
bundle exec rake db:drop >/dev/null 2>/dev/null || true
bundle exec rake db:create db:migrate --trace RAILS_ENV=test

bundle exec rake pkg:generate_source jenkins:unit jenkins:integration TESTOPTS="-v" --trace

# Test asset precompile
rm -f db/schema.rb
cp db/schema.rb.nulldb db/schema.rb
bundle exec rake assets:precompile RAILS_ENV=production DATABASE_URL=nulldb://nohost
bundle exec rake webpack:compile RAILS_ENV=production DATABASE_URL=nulldb://nohost
