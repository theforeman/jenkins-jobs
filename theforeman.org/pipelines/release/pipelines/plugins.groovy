pipeline {
    agent { label 'el' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    environment {
        PROJECT = 'plugins'
    }

    script {
        env.VERSION = foreman_version
    }

    stages {
        stage('staging-repository') {
            // No when condition because it's not signed

            steps {
                script {
                    rsync_to_yum_stage
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures('plugins-staging', foreman_el_releases, foreman_version)
                }
            }
            post {
                always {
                    deleteDir()
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'sshkey' }

            steps {
                script {
                    foreman_el_releases.each { distro ->
                        push_foreman_staging_rpms('plugins', foreman_version, distro)
                    }
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "Plugins ${foreman_version} pipeline failed:", currentBuild.description)
        }
    }
}
