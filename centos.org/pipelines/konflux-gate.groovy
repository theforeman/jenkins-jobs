def GATE_IMAGE_REFS = [:]

pipeline {
    agent { label 'cico-workspace' }

    options {
        timestamps()
        ansiColor('xterm')
        timeout(time: 3, unit: 'HOURS')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        // Preserve HOME and the pod's existing tenant SSH key/configuration.
        PATH = "${WORKSPACE}/duffy-venv/bin:${env.PATH}"
    }

    stages {
        stage('Validate image set') {
            steps {
                deleteDir()
                script {
                    GATE_IMAGE_REFS = readJSON(text: params.IMAGE_REFS, returnPojo: true)
                    konflux_gate_validate_inputs(GATE_IMAGE_REFS, konflux_components, params.FOREMANCTL_BRANCH)
                    writeJSON(file: 'image-refs.json', json: GATE_IMAGE_REFS)
                    archiveArtifacts artifacts: 'image-refs.json'
                }
            }
        }
        stage('Setup Duffy client') {
            steps {
                sh(label: 'install Duffy Python runtime', script: '''
                    python3.12 -m venv duffy-venv
                    # evgeni.duffy now expects the dictionary responses from Duffy 3.4.
                    duffy-venv/bin/python -m pip install ansible-core 'duffy[client]>=3.4,<3.5'
                ''')
                setupDuffyClient()
            }
        }
        stage('Test pinned images') {
            steps {
                script {
                    konflux_gate_test_on_duffy(GATE_IMAGE_REFS, params.FOREMANCTL_BRANCH, params.PYTEST_ARGS)
                }
            }
        }
    }

    post {
        cleanup {
            script {
                try {
                    // Retirement is idempotent. Retry here if the runner's
                    // finally was interrupted or its first API call failed.
                    deprovisionDuffy()
                } finally {
                    try {
                        sh(label: 'remove Duffy client config', script: 'rm -f -- "$HOME/.config/duffy"')
                    } finally {
                        deleteDir()
                    }
                }
            }
        }
    }
}
