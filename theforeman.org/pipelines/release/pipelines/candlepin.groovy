pipeline {
    agent { label 'el8' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('staging-build-repository') {
            when {
                expression { candlepin_version == 'nightly' }
            }
            environment {
                PROJECT = 'candlepin'
                VERSION = candlepin_version
                RSYNC_RSH = "ssh -i ${ssh_key}"
            }
            steps {
                rel_eng_clone()
                rel_eng_build_stage()
                rel_eng_upload_stage()
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
        stage('staging-install-test') {
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
