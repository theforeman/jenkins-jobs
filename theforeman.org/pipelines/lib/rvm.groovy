def gemset(name = null) {

    def base_name = "${JOB_NAME}-${BUILD_NUMBER}"

    if (EXECUTOR_NUMBER != '0') {
        base_name += '-' + EXECUTOR_NUMBER
    }

    if (name) {
        base_name += '-' + name.replace(".", "-")
    }

    base_name
}

def configureRVM(ruby, name = '', bundler_version = null) {
    emptyGemset(ruby, name)

    if (ruby == '2.7') {
        bundler_version = '2.4.22'
    }

    if (bundler_version) {
        withRVM(["gem install bundler -v '${bundler_version}' --no-document"], ruby, name)
    } else {
        withRVM(["gem install bundler --no-document"], ruby, name)
    }
}

def emptyGemset(ruby, name = '') {
    withRVM(["rvm gemset empty ${gemset(name)} --force"], ruby, name)
}

def cleanupRVM(ruby, name = '') {
    withRVM(["rvm gemset delete ${gemset(name)} --force"], ruby, name)
}

def withRVM(commands, ruby, name = '') {

    commands = commands.join("\n")
    echo commands.toString()

    sh(script: """#!/bin/bash -l
        set -e
        rvm use ruby-${ruby}@${gemset(name)} --create
        ${commands}
    """, label: "${commands}")
}
