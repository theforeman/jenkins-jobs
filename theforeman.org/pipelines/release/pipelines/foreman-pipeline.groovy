pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('Mash Koji Repositories') {
            agent { label 'sshkey' }

            when {
                expression { stage_source == 'koji' }
            }

            steps {
                mash("foreman", foreman_version)
            }
        }
        stage('Koji Repoclosure') {
            agent { label 'el' }

            when {
                expression { stage_source == 'koji' }
            }

            steps {
                script {
                    parallel repoclosures('foreman', foreman_el_releases, foreman_version)
                }
            }
            post {
                always {
                    deleteDir()
                }
            }
        }

        stage('Staging Repoclosure') {
            agent { label 'el8' }

            when {
                expression { stage_source == 'stagingyum' }
            }

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
        stage('Install Test') {
            agent any

            steps {
                script {
                    runDuffyPipelines(['foreman-rpm', 'foreman-deb'], foreman_version, params.expected_version)
                }
            }
        }
        stage('Push Koji RPMs') {
            agent { label 'admin && sshkey' }

            when {
                expression { stage_source == 'koji' }
            }

            steps {
                script {
                    for (release in foreman_el_releases) {
                        push_rpms_direct("foreman-${foreman_version}/${release}", "releases/${foreman_version}/${release}", false, true)
                    }
                }
            }
        }

        stage('Push Staging RPMs') {
            agent { label 'admin && sshkey' }

            when {
                expression { stage_source == 'stagingyum' }
            }

            steps {
                script {
                    foreman_el_releases.each { distro ->
                        push_foreman_staging_rpms('foreman', foreman_version, distro)
                    }
                }
            }
        }

        stage('Push DEBs') {
            agent { label 'debian' }

            steps {
                script {
                    def pushDistros = [:]
                    foreman_debian_releases.each { distro ->
                        pushDistros["push-${foreman_version}-${distro}"] = {
                            script {
                                push_debs_direct(distro, foreman_version)
                            }
                        }
                    }

                    parallel pushDistros
                }
            }
        }
    }

    post {
        success {
          build job: "foreman-plugins-${foreman_version}-rpm-test-pipeline", wait: false
          build job: "foreman-plugins-${foreman_version}-deb-test-pipeline", wait: false
        }
    }
}
