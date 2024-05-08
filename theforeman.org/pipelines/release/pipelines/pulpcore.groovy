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
            when {
                expression { pulpcore_version == 'nightly' }
            }
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
            when {
                expression { pulpcore_version == 'nightly' }
            }
            steps {
                script {
                    rsync_to_yum_stage('pulpcore', pulpcore_version)
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
