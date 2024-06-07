pipeline {
    agent { label 'el' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
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
                    parallel repoclosures('foreman-client-staging', foreman_client_distros, foreman_version)
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
