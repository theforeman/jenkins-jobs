pipeline {
    agent { label 'sshkey' }

    options {
        ansiColor('xterm')
        buildDiscarder(logRotator(numToKeepStr: '3'))
        disableConcurrentBuilds()
        timestamps()
    }

    stages {
        stage('Execute shell') {
            steps {
                git_clone_foreman_infra()
                sshagent(['puppet-deploy']) {
                    sh 'ssh deploypuppet@puppet.theforeman.org'
                }
            }
        }
    }
}
