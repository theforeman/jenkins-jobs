#!/bin/bash -ex

rm -rf projects/
mkdir projects
cd projects
git clone https://github.com/theforeman/foreman --branch "${foreman_branch}"
git clone https://github.com/theforeman/foreman_rh_cloud
git clone https://github.com/theforeman/foreman-tasks --branch "${foreman_tasks_branch}"
git clone https://github.com/katello/katello --branch "${katello_branch}"
cd foreman
echo "gemspec :path => '../foreman_rh_cloud', :development_group => :dev" > bundler.d/foreman_rh_cloud.local.rb
echo "gemspec :path => '../katello', :development_group => :dev" > bundler.d/katello.local.rb
cp ../foreman_rh_cloud/config/database.yml.example config/database.yml
cp ../foreman_rh_cloud/config/Gemfile.lock.gh_test Gemfile.lock
cp ../foreman_rh_cloud/config/package-lock.json.gh_test package-lock.json
gem install bundler
bundle config path vendor/bundle
bundle install --jobs=3 --retry=3 --without journald development mysql2 console
run: npm ci
bundle exec rails db:create
bundle exec rails db:create RAILS_ENV=production
bundle exec rails db:migrate
bundle exec rake foreman_rh_cloud:rubocop
bundle exec rake test:foreman_rh_cloud
bundle exec rake "plugin:assets:precompile[foreman_rh_cloud]" RAILS_ENV=production
bundle exec rake webpack:compile
