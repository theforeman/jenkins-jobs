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
        PIPELINE = "${PROJECT}-rpm"
        REPOCLOSURE = "${PROJECT}-staging"
    }

    script {
        env.DISTROS = foreman_el_releases
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
                    parallel repoclosures(env.REPOCLOSURE, env.DISTROS, env.VERSION)
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

            when { not { environment name: 'PIPELINE', value: '' } }

            steps {
                script {
                    runDuffyPipeline(env.PIPELINE, env.VERSION)
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'sshkey' }

            steps {
                script {
                    env.DISTROS.each { distro ->
                        push_foreman_staging_rpms(env.PROJECT, env.VERSION, distro)
                    }
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "${env.PROJECT} ${env.VERSION} RPM pipeline failed:", currentBuild.description)
        }
    }
}
