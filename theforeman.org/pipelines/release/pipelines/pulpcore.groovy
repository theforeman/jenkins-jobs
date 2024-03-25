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
                        expression { pulpcore_version == 'nightly' }
                    }
                    environment {
                        PROJECT = 'pulpcore'
                        VERSION = pulpcore_version
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
                    agent { label 'sshkey' }

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
