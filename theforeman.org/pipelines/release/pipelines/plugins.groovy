pipeline {
    agent { label 'el8' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    environment {
        PROJECT = 'plugins'
    }

    script {
        env.VERSION = foreman_version
    }

    stages {
        stage('staging-repository') {
            steps {
                script {
                    rsync_to_yum_stage
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures("${env.PROJECT}-staging", foreman_el_releases, env.VERSION)
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
                        push_foreman_staging_rpms(env.PROJECT, env.VERSION, distro)
                    }
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "${env.PROJECT} ${env.VERSION} RPM pipeline failed:", currentBuild.description)
        }
    }
}
