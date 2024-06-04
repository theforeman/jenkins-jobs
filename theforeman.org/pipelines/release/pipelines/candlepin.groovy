pipeline {
    agent { label 'el8' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    environment {
        PROJECT = 'candlepin'
    }

    script {
        env.VERSION = candlepin_version
    }

    stages {
        stage('staging-repository') {
            when {
                expression { env.VERSION == 'nightly' }
            }
            steps {
                script {
                    rsync_to_yum_stage
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures(env.PROJECT, candlepin_distros, env.VERSION)
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
                    runDuffyPipeline("${env.PROJECT}-rpm", env.VERSION)
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'sshkey' }

            steps {
                script {
                    candlepin_distros.each { distro ->
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
