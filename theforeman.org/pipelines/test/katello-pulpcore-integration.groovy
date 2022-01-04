def pipelines = [
    'katello_devel': [
        'centos7',
        'centos8-stream'
    ]
]

pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('Test Integration') {
            agent any

            steps {
                script {
                    runCicoPipelines('katello_devel', 'devel', pipelines)
                }
            }
        }
    }
}
