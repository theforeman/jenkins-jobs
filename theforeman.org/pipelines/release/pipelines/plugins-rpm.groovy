pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('Install Test') {
            agent any

            steps {
                script {
                    runDuffyPipeline('plugins-rpm', foreman_version)
                }
            }
        }
    }

    post {
        failure {
            notifyDiscourse(env, "Foreman ${foreman_version} Plugins RPM Test pipeline failed:", currentBuild.description)
        }
    }
}
