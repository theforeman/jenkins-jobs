pipeline {
    agent { label 'el' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '7'))
    }

    stages {
        stage("Collect Git Hash") {
            steps {
                git url: git_url, branch: git_ref
                script {
                    archive_git_hash()
                }
            }
        }
        stage("Build for rhel7") {
            steps {
                script {
                    instprefix = pwd(tmp: true)
                }
                sh "make INSTPREFIX=${instprefix}"
            }
        }
        stage('Build and Archive Source') {
            steps {
                dir(project_name) {
                    git url: git_url, branch: git_ref
                }
                script {
                    generate_sourcefiles(project_name: project_name, source_type: source_type)
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "${project_name} source release pipeline failed:", currentBuild.description)
        }

        cleanup {
            deleteDir()
        }
    }
}
