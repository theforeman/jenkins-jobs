def el_pipelines = [
    'install': [
        'centos9-stream',
        'almalinux9',
    ],
    'upgrade': [
        'centos9-stream',
        'almalinux9',
    ]
]

def deb_pipelines = [
    'install': [
        'debian12',
        'ubuntu2204'
    ],
    'upgrade': [
        'debian12',
        'ubuntu2204'
    ]
]

def candlepin_pipelines = [
    'candlepin': [
        'centos9-stream',
        'almalinux9',
    ]
]

def pulpcore_pipelines = [
    'pulpcore': [
        'centos9-stream',
        'almalinux9',
    ]
]

pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
        ansiColor('xterm')
    }

    stages {
        stage('Run Integration Test') {
            steps {
                script {
                    def test_pipelines
                    switch (params.type) {
                        case 'candlepin':
                            test_pipelines = candlepin_pipelines
                            break
                        case 'pulpcore':
                            test_pipelines = pulpcore_pipelines
                            break
                        default:
                            test_pipelines = (params.flavor == 'deb') ? deb_pipelines : el_pipelines
                            break
                    }

                    runDuffyTest(
                        pipelines: test_pipelines,
                        type: params.type,
                        version: params.version,
                        expected_version: params.expected_version
                    )
                }
            }
        }
    }

    post {
        failure {
            notifyDiscourse(env, "${params.type} ${params.version} ${params.flavor} integration test failed:", currentBuild.description)
        }
    }
}
