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
                            dir('tmp') {
                                rsync_to_yum_stage('client', 'client', foreman_version)
                            }
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
