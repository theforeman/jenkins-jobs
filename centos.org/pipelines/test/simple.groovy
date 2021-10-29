pipeline {
    agent { label 'cico-workspace' }

    stages {
        stage('Echo') {
            steps {
                sh "echo"
            }
        }
    }
}
