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
                        mash('foreman-plugins', foreman_version)
                    }
                }
                stage('koji-repoclosure') {
                    agent { label 'el' }

                    steps {
                        script {
                            parallel repoclosures('plugins', foreman_el_releases, foreman_version)
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
                            def overwrite = foreman_version == 'nightly'
                            def merge = foreman_version != 'nightly'
                            for (release in foreman_el_releases) {
                                push_rpms_direct("foreman-plugins-${foreman_version}/${release}", "plugins/${foreman_version}/${release}", overwrite, merge)
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
                    steps {
                        git url: "https://github.com/theforeman/theforeman-rel-eng", poll: false

                        script {
                            foreman_el_releases.each { distro ->
                                sh "./build_stage_repository plugins ${foreman_version} ${distro}"
                            }
                        }
                    }
                }
                stage('staging-copy-repository') {
                    steps {
                        script {
                            dir('tmp') {
                                rsync_to_yum_stage('plugins', 'plugins', foreman_version)
                            }
                        }
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
                    agent { label 'admin && sshkey' }

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
