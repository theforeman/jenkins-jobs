pipeline {
    agent { label 'el8' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
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
                    rsync_to_yum_stage('plugins', foreman_version)
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures('plugins-staging', foreman_el_releases, foreman_version)
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
                    foreman_el_releases.each { distro ->
                        push_foreman_staging_rpms('plugins', foreman_version, distro)
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
