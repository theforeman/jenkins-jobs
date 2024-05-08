pipeline {
    agent { label 'el8' }

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
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
                    rsync_to_yum_stage('katello', katello_version)
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
        stage('staging-test') {
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
    post {
        failure {
            notifyDiscourse(env, "Katello ${katello_version} pipeline failed:", currentBuild.description)
        }
    }
}
