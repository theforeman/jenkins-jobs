pipeline {
    agent { label 'sshkey' }

    options {
        ansiColor('xterm')
        buildDiscarder(logRotator(numToKeepStr: '3'))
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        ruby = '3.0.4'
        // Sync to the pivot-point on the web node
        target_path = 'website@website01.osuosl.theforeman.org:rsync_cache/'
        rsync_log = 'deploy-website.log'
    }

    stages {
        stage('Deploy website') {
            steps {
                git url: 'https://github.com/theforeman/theforeman.org', branch: 'gh-pages'

                script {
                    bundleInstall(ruby)
                    bundleExec(ruby, "jekyll build")
                }

                sshagent(['deploy-website']) {
                    // Copy the site to the web node
                    // Dependencies
                    // * the web node must have the web class
                    sh "/usr/bin/rsync --log-file '${rsync_log}' --log-file-format 'CHANGED %f' --archive --checksum --verbose --one-file-system --compress --stats --delete-after ./_site/ ${target_path}"
                }

                sh "awk '/ CHANGED /{print \$5}' '${rsync_log}' | sed 's#_site/##' | xargs --no-run-if-empty fastly-purge 'https://theforeman.org/'"
                sh "rm '${rsync_log}'"
            }
        }
    }
}
