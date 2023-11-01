pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('koji') {
            when {
                expression { stage_source == 'koji' }
            }
            stages {
                stage('koji-mash-repositories') {
                    agent { label 'sshkey' }

                    steps {
                        mash('foreman-client', foreman_version)
                    }
                }
                stage('koji-repoclosure') {
                    agent { label 'el' }

                    steps {
                        script {
                            def parallelStagesMap = [:]
                            def name = 'foreman-client'
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
                stage('koji-push-rpms') {
                    agent { label 'admin && sshkey' }

                    steps {
                        script {
                            foreman_client_distros.each { distro ->
                                push_foreman_rpms('client', foreman_version, distro)
                            }
                        }
                    }
                }
            }
        }
        stage('staging') {
            agent { label 'el8' }
            when {
                expression { stage_source == 'stagingyum' }
            }
            stages {
                stage('staging-build-repository') {
                    when {
                        expression { foreman_version == 'nightly' }
                    }
                    steps {
                        git url: "https://github.com/theforeman/theforeman-rel-eng", poll: false

                        script {
                            foreman_client_distros.each { distro ->
                                sh "./build_stage_repository client ${foreman_version} ${distro}"
                            }
                        }
                    }
                }
                stage('staging-copy-repository') {
                    when {
                        expression { foreman_version == 'nightly' }
                    }
                    steps {
                        script {
                            rsync_to_yum_stage('client', foreman_version)
                        }
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
                    agent { label 'admin && sshkey' }

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
