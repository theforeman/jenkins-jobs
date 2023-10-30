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
                        mash('pulpcore', pulpcore_version)
                    }
                }
                stage('koji-repoclosure') {
                    agent { label 'el' }

                    steps {
                        script {
                            def parallelStagesMap = [:]
                            def name = 'pulpcore'
                            pulpcore_distros.each { distro ->
                                parallelStagesMap[distro] = { repoclosure(name, distro, pulpcore_version) }
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
                stage('koji-test') {
                    agent any

                    steps {
                        script {
                            runDuffyPipeline('pulpcore-rpm', pulpcore_version)
                        }
                    }
                }
                stage('koji-push-rpms') {
                    agent { label 'admin && sshkey' }

                    steps {
                        script {
                            pulpcore_distros.each { distro ->
                                push_pulpcore_rpms(pulpcore_version, distro)
                            }
                        }
                    }
                }
            }
            post {
                failure {
                    notifyDiscourse(env, "Pulpcore ${pulpcore_version} RPM pipeline failed:", currentBuild.description)
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
                            pulpcore_distros.each { distro ->
                                sh "./build_stage_repository pulpcore ${pulpcore_version} ${distro}"
                            }
                        }
                    }
                }
                stage('staging-copy-repository') {
                    steps {
                        script {
                            dir('tmp') {
                                rsync_to_yum_stage('pulpcore', 'pulpcore', pulpcore_version)
                            }
                        }
                    }
                }
                stage('staging-repoclosure') {
                    steps {
                        script {
                            def parallelStagesMap = [:]
                            def name = 'pulpcore-staging'
                            pulpcore_distros.each { distro ->
                                parallelStagesMap[distro] = { repoclosure(name, distro, pulpcore_version) }
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
                stage('staging-test') {
                    agent any

                    steps {
                        script {
                            runDuffyPipeline('pulpcore-rpm', pulpcore_version)
                        }
                    }
                }
                stage('staging-push-rpms') {
                    agent { label 'admin && sshkey' }

                    steps {
                        script {
                            pulpcore_distros.each { distro ->
                                push_foreman_staging_rpms('pulpcore', pulpcore_version, distro)
                            }
                        }
                    }
                }
            }
        }
    }
}
