pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
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
                        mash("katello", katello_version)
                    }
                }
                stage('koji-repoclosure') {
                    agent { label 'el' }

                    steps {
                        script {
                            parallel repoclosures('katello', foreman_el_releases, foreman_version)
                        }
                    }
                    post {
                        always {
                            deleteDir()
                        }
                    }
                }
                stage('koji-install-test') {
                    agent any

                    steps {
                        script {
                            runDuffyPipeline('katello-rpm', katello_version)
                        }
                    }
                }
                stage('koji-push-rpms') {
                    agent { label 'sshkey' }

                    steps {
                        script {
                            foreman_el_releases.each { distro ->
                                push_katello_rpms(katello_version, distro)
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
                        expression { katello_version == 'nightly' }
                    }
                    steps {
                        git url: "https://github.com/theforeman/theforeman-rel-eng", poll: false

                        script {
                            foreman_el_releases.each { distro ->
                                sh "./build_stage_repository katello ${katello_version} ${distro} ${foreman_version}"
                            }
                        }
                    }
                }
                stage('staging-copy-repository') {
                    when {
                        expression { katello_version == 'nightly' }
                    }
                    steps {
                        script {
                            dir('tmp') {
                                rsync_to_yum_stage('katello', 'katello', katello_version)
                            }
                        }
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
