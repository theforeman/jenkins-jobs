pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('staging') {
            agent { label 'el8' }
            stages {
                stage('staging-build-repository') {
                    when {
                        expression { foreman_version == 'nightly' }
                    }
                    environment {
                        PROJECT = 'foreman'
                        VERSION = foreman_version
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
                            parallel repoclosures('foreman-staging', foreman_el_releases, foreman_version)
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
        }
    }
    post {
        failure {
            notifyDiscourse(env, 'Foreman RPM nightly pipeline failed:', currentBuild.description)
        }
    }
}
