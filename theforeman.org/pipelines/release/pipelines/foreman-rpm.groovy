pipeline {
    agent { label 'el' }

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    environment {
        PROJECT = 'foreman'
    }

    script {
        env.VERSION = foreman_version
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
                    parallel repoclosures('foreman-staging', foreman_el_releases, foreman_version)
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
                    runDuffyPipeline('foreman-rpm', foreman_version)
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'sshkey' }

            steps {
                script {
                    foreman_el_releases.each { distro ->
                        push_foreman_staging_rpms('foreman', foreman_version, distro)
                    }
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, 'Foreman RPM nightly pipeline failed:', currentBuild.description)
        }
    }
}
