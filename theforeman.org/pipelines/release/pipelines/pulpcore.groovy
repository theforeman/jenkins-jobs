pipeline {
    agent { label 'el8' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    environment {
        PROJECT = 'pulpcore'
    }

    script {
        env.VERSION = pulpcore_version
    }

    stages {
        stage('staging-repository') {
            when {
                expression { env.VERSION == 'nightly' }
            }
            steps {
                script {
                    rsync_to_yum_stage
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures('pulpcore-staging', foreman_el_releases, foreman_version)
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
    post {
        failure {
            notifyDiscourse(env, "Pulpcore ${pulpcore_version} RPM pipeline failed:", currentBuild.description)
        }
    }
}
