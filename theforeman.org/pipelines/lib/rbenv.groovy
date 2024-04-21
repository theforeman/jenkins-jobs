def bundleInstall(version, options=null) {
    command = "bundle install --jobs 5 --retry 5"

    if (options) {
        command = "${command} ${options}"
    }

    withRuby(version, "bundle config set path ~/.rubygems")
    withRuby(version, command)
}

def bundleExec(version, command, gemfile=null) {
    command = "bundle exec ${command}"

    if (gemfile) {
        command = "BUNDLE_GEMFILE=${gemfile} ${command}"
    }

    withRuby(version, command)
}

def withRuby(version, command) {
    echo command.toString()

    sh """
        export PATH="\$HOME/.rbenv/shims:\$PATH"
        export RBENV_VERSION=${version}
        ${command}
    """
}
