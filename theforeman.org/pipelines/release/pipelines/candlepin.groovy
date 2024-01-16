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
                expression { candlepin_version == 'nightly' }
            }
            steps {
                git url: "https://github.com/theforeman/theforeman-rel-eng", poll: false

                script {
                    candlepin_distros.each { distro ->
                        sh "./build_stage_repository candlepin ${candlepin_version} ${distro}"
                    }
                }
            }
        }
        stage('staging-copy-repository') {
            when {
                expression { candlepin_version == 'nightly' }
            }
            steps {
                script {
                    dir('tmp') {
                        rsync_to_yum_stage('candlepin', 'candlepin', candlepin_version)
                    }
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures('candlepin', candlepin_distros, candlepin_version)
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
                    runDuffyPipeline('candlepin', candlepin_version)
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'admin && sshkey' }

            steps {
                script {
                    candlepin_distros.each { distro ->
                        push_foreman_staging_rpms('candlepin', candlepin_version, distro)
                    }
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "Candlepin ${candlepin_version} RPM pipeline failed:", currentBuild.description)
        }
    }
}
