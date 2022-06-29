pipeline {
    agent { label 'el' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        ansiColor('xterm')
    }

    stages {
        stage("Collect Git Hash") {
            steps {
                git(url: 'https://github.com/theforeman/foreman-selinux', branch: 'develop')
                script {
                    archive_git_hash()
                }
            }
        }
        stage("Build for rhel8") {
            steps {
                script {
                    distro = 'rhel8'
                    instprefix = pwd(tmp: true)
                }
                sh "make INSTPREFIX=${instprefix}/${distro} DISTRO=${distro}"
            }
        }
        stage("Release Nightly Package") {
            steps {
                build(
                    job: 'foreman-selinux-develop-release',
                    propagate: false,
                    wait: false
                )
            }
        }
    }
    post {
        always {
            deleteDir()
        }
    }
}
