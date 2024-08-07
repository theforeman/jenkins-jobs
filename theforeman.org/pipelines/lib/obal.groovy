def obal(args) {
    def extra_vars = args.extraVars ?: [:]
    def extra_vars_file

    def packages = args.packages
    if (packages instanceof String[]) {
        packages = packages.join(' ')
    }

    python = 'python3'

    def cmd = "${python} -m obal ${args.action} ${packages}"

    if (extra_vars) {
        extra_vars_file = writeExtraVars(extraVars: extra_vars)
        cmd = "${cmd} -e @${extra_vars_file}"
    }

    if (args.action == 'release' || args.action == 'scratch' || args.action == 'nightly') {
        cmd = "${cmd} --skip-koji-whitelist-check"
    }

    wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
        withEnv(['ANSIBLE_FORCE_COLOR=true', "PYTHONPATH=${env.WORKSPACE}/obal:${env.WORKSPACE}/obsah"]) {
            sh "${cmd}"
        }
    }

    if (extra_vars) {
        sh "rm ${extra_vars_file}"
    }
}

def setup_obal() {
    dir("${env.WORKSPACE}/obsah") {
        checkout(
            changelog: false,
            poll: false,
            scm: [
                $class: 'GitSCM',
                branches: [[name: 'master']],
                extensions: [[$class: 'CleanCheckout']],
                userRemoteConfigs: [
                    [url: 'https://github.com/theforeman/obsah']
                ]
            ]
        )
    }

    dir("${env.WORKSPACE}/obal") {
        checkout(
            changelog: false,
            poll: false,
            scm: [
                $class: 'GitSCM',
                branches: [[name: 'master']],
                extensions: [[$class: 'CleanCheckout']],
                userRemoteConfigs: [
                    [url: 'https://github.com/theforeman/obal']
                ]
            ]
        )
    }
}
