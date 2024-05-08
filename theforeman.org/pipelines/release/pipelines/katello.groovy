pipeline {
    agent { label 'el' }

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    environment {
        PROJECT = 'katello'
    }

    script {
        env.VERSION = katello_version
    }

    stages {
        stage('staging-repository') {
            when { environment name: 'VERSION', value: 'nightly' }

            steps {
                script {
                    rsync_to_yum_stage
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures('katello-staging', foreman_el_releases, foreman_version)
                }
            }
            post {
                always {
                    deleteDir()
                }
            }
        }
        stage('staging-test') {
            agent any

            steps {
                script {
                    runDuffyPipeline('katello-rpm', katello_version)
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'sshkey' }

            steps {
                script {
                    foreman_el_releases.each { distro ->
                        push_foreman_staging_rpms('katello', katello_version, distro)
                    }
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "Katello ${katello_version} pipeline failed:", currentBuild.description)
        }
    }
}
