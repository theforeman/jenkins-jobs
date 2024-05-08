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
        VERSION = candlepin_version
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
                    parallel repoclosures('candlepin', candlepin_distros, candlepin_version)
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
                    runDuffyPipeline('candlepin-rpm', candlepin_version)
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'sshkey' }

            steps {
                script {
                    candlepin_distros.each { distro ->
                        push_foreman_staging_rpms('candlepin', candlepin_version, distro)
                    }
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "Candlepin ${candlepin_version} RPM pipeline failed:", currentBuild.description)
        }
    }
}
