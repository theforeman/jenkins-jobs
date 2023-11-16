pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
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
                        mash('foreman', 'nightly')
                    }
                }
                stage('koji-repoclosure') {
                    agent { label 'el' }

                    steps {
                        script {
                            parallel repoclosures('foreman', foreman_el_releases, foreman_version)
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
                            runDuffyPipeline('foreman-rpm', foreman_version)
                        }
                    }
                }
                stage('koji-push-rpms') {
                    agent { label 'admin && sshkey' }
                    steps {
                        script {
                            for (release in foreman_el_releases) {
                                push_rpms_direct("foreman-${foreman_version}/${release}", "${foreman_version}/${release}")
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
                            foreman_el_releases.each { distro ->
                                sh "./build_stage_repository foreman ${foreman_version} ${distro}"
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
                                rsync_to_yum_stage('foreman', 'foreman', foreman_version)
                            }
                        }
                    }
                }
                stage('staging-repoclosure') {
                    steps {
                        script {
                            def parallelStagesMap = [:]
                            def name = 'foreman-staging'
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
                            runDuffyPipeline('foreman-rpm', foreman_version)
                        }
                    }
                }
                stage('staging-push-rpms') {
                    agent { label 'admin && sshkey' }

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
