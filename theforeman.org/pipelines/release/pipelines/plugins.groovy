pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('staging') {
            agent { label 'el8' }
            stages {
                stage('staging-build-repository') {
                    environment {
                        PROJECT = 'plugins'
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
                            def parallelStagesMap = [:]
                            def name = 'plugins-staging'
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
        }
    }
    post {
        failure {
            notifyDiscourse(env, "Plugins ${foreman_version} pipeline failed:", currentBuild.description)
        }
    }
}
