pipeline {
    agent { label 'el' }

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
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
                    rsync_to_yum_stage('foreman', foreman_version)
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures('foreman-staging', foreman_el_releases, foreman_version)
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
                    runDuffyPipeline('foreman-rpm', foreman_version)
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'sshkey' }

            steps {
                script {
                    foreman_el_releases.each { distro ->
                        push_foreman_staging_rpms('foreman', foreman_version, distro)
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
