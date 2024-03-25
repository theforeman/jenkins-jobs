pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('staging') {
            agent { label 'el8' }
            stages {
                stage('staging-build-repository') {
                    when {
                        expression { katello_version == 'nightly' }
                    }
                    environment {
                        PROJECT = 'katello'
                        VERSION = katello_version
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
                            def parallelStagesMap = [:]
                            def name = 'katello-staging'
                            foreman_el_releases.each { distro ->
                                parallelStagesMap[distro] = { repoclosure(name, distro, foreman_version) }
                            }
                            parallel parallelStagesMap
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
        }
    }
    post {
        failure {
            notifyDiscourse(env, "Katello ${katello_version} pipeline failed:", currentBuild.description)
        }
    }
}
