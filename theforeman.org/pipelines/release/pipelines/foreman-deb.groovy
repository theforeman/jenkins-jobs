pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('Install tests') {
            agent any

            steps {
                script {
                    runDuffyPipeline('foreman-deb', foreman_version)
                }
            }
        }
        stage('Push DEBs') {
            agent { label 'debian' }

            steps {
                script {
                    def pushDistros = [:]
                    foreman_debian_releases.each { distro ->
                        pushDistros["push-${foreman_version}-${distro}"] = {
                            script {
                                push_debs_direct(distro, foreman_version)
                            }
                        }
                    }

                    parallel pushDistros
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, 'Foreman DEB nightly pipeline failed:', currentBuild.description)
        }
    }
}
