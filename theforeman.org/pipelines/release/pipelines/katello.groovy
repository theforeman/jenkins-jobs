pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('Mash Koji Repositories') {
            agent { label 'sshkey' }

            steps {
                mash("katello", katello_version)
            }
        }
        stage('Katello Repoclosure') {
            agent { label 'el' }

            steps {
                script {
                    parallel repoclosures('katello', foreman_el_releases, foreman_version)
                }
            }
            post {
                always {
                    deleteDir()
                }
            }
        }
        stage('Test Suites') {
            agent any

            steps {
                script {
                    runDuffyPipeline('katello', katello_version)
                }
            }
        }
        stage('Push RPMs') {
            agent { label 'admin && sshkey' }

            steps {
                script {
                    foreman_el_releases.each { distro ->
                        push_katello_rpms(katello_version, distro)
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
