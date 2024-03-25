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
                    when {
                        expression { foreman_version == 'nightly' }
                    }
                    environment {
                        PROJECT = 'client'
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
                            def name = 'foreman-client-staging'
                            foreman_client_distros.each { distro ->
                                if (distro.startsWith('el')) {
                                    parallelStagesMap[distro] = { repoclosure(name, distro, foreman_version) }
                                } else if (distro.startsWith('fc')) {
                                    parallelStagesMap[distro] = { repoclosure(name, distro.replace('fc', 'f'), foreman_version) }
                                }
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
                            foreman_client_distros.each { distro ->
                                push_foreman_staging_rpms('client', foreman_version, distro)
                            }
                        }
                    }
                }
            }
        }
    }
}
