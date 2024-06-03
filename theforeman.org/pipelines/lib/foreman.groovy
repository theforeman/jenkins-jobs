def databaseFile(id) {
    text = postgresqlTemplate(id)
    writeFile(file: 'config/database.yml', text: text)
}

def addGem() {
    writeFile(text: "gemspec :path => '../', :development_group => :dev\n", file: 'bundler.d/Gemfile.local.rb')
    if(fileExists('../gemfile.d/')) {
        sh "cat ../gemfile.d/*.rb >> bundler.d/Gemfile.local.rb"
    }
}

def addSettings(settings) {
    sh "cp config/settings.yaml.example config/settings.yaml"
}

def configureDatabase(ruby) {
    bundleInstall(ruby, '--without=development')
    archiveArtifacts(artifacts: 'Gemfile.lock')
    bundleExec(ruby, 'rake db:drop >/dev/null 2>/dev/null || true')
    bundleExec(ruby, 'rake db:create --trace')
    bundleExec(ruby, 'rake db:create --trace RAILS_ENV=production')
    bundleExec(ruby, 'rake db:migrate --trace')
}

def cleanup(ruby) {
    bundleExec(ruby, 'rake db:drop RAILS_ENV=production DISABLE_DATABASE_ENVIRONMENT_CHECK=true >/dev/null 2>/dev/null || true')
    bundleExec(ruby, 'rake db:drop RAILS_ENV=test DISABLE_DATABASE_ENVIRONMENT_CHECK=true >/dev/null 2>/dev/null || true')
    bundleExec(ruby, 'rake db:drop RAILS_ENV=development DISABLE_DATABASE_ENVIRONMENT_CHECK=true >/dev/null 2>/dev/null || true')
}

def postgresqlTemplate(id) {
  if ("test-${id}-test".size() > 63) {
    error "${id} cannot be used to generate a PostgreSQL DB name, the resulting name would be longer than 63 chars."
  }
  return """
test:
  adapter: postgresql
  database: test-${id}-test
  username: foreman
  password: foreman
  host: localhost
  template: template0

development:
  adapter: postgresql
  database: test-${id}-dev
  username: foreman
  password: foreman
  host: localhost
  template: template0

production:
  adapter: postgresql
  database: test-${id}-prod
  username: foreman
  password: foreman
  host: localhost
  template: template0
"""
}

def filter_package_json(ruby) {
    if (env.NODE_LABELS.contains('el8')) {
        python = 'python3.12'
    } else {
        python = 'python3'
    }

    sh "${python} script/filter-package-json.py"

    bundleExec(ruby, "ruby script/plugin_webpack_directories.rb > plugin_webpack.json")
    def plugin_webpack = readJSON file: 'plugin_webpack.json'
    plugin_webpack['plugins'].each { plugin, config ->
        sh "${python} script/filter-package-json.py --package-json ${config['root']}/package.json"
    }
}
