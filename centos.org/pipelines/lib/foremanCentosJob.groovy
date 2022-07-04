playBook = pipelineVars(action: params.action, type: params.type, version: params.version, os: params.distro, extra_vars: ['foreman_expected_version': params.expected_version ?: ''])

pipeline {
    agent { label 'cico-workspace' }

    options {
        timestamps()
        ansiColor('xterm')
        buildDiscarder(logRotator(numToKeepStr: '3'))
    }

    environment {
        ANSIBLE_CALLBACK_WHITELIST = 'community.general.opentelemetry'
    }

    stages {
        stage('Setup Environment') {
            steps {
                deleteDir()
                git url: 'https://github.com/theforeman/forklift.git'

                // old pip doesn't use binary wheels, and we really do not want to compile the otel sdk
                sh(label: 'update pip', script: 'pip3 install -U pip --user')
                sh(label: 'pip install', script: '~/.local/bin/pip install opentelemetry-api opentelemetry-sdk opentelemetry-exporter-otlp')
            }
        }
        stage('Provision Node') {
            steps {
                provision()
            }
        }
        stage('Install Pipeline Requirements') {
            steps {
                script {
                    if (params.type == 'pulpcore') {
                        setup_extra_vars = ['forklift_install_pulp_from_galaxy': true, 'forklift_install_from_galaxy': false, 'pipeline_version': params.version]
                    } else {
                        setup_extra_vars = ['forklift_telemetry': true]
                    }
                    runPlaybook(
                        playbook: 'playbooks/setup_forklift.yml',
                        inventory: cico_inventory('./'),
                        options: ['-b'],
                        extraVars: setup_extra_vars,
                        commandLineExtraVars: true,
                    )
                }
            }
        }
        stage('Run Pipeline') {
            steps {
                script {
                    extra_vars = buildExtraVars(extraVars: playBook['extraVars'])

                    def otel_env = """
                    export OTEL_EXPORTER_OTLP_HEADERS='${env.OTEL_EXPORTER_OTLP_HEADERS}'
                    export OTEL_EXPORTER_OTLP_ENDPOINT='${env.OTEL_EXPORTER_OTLP_ENDPOINT}'
                    export OTEL_TRACES_EXPORTER='${env.OTEL_TRACES_EXPORTER}'
                    export TRACE_ID='${env.TRACE_ID}'
                    export SPAN_ID='${env.SPAN_ID}'
                    export TRACEPARENT='${env.TRACEPARENT}'
                    export TRACESTATE='${env.TRACESTATE}'
                    export ANSIBLE_CALLBACK_WHITELIST='${env.ANSIBLE_CALLBACK_WHITELIST}'
                    """

                    writeFile(file: 'otel_env', text: otel_env)
                    duffy_scp_in('otel_env', 'otel_env', 'duffy_box', './')
                    sh(script: 'rm -rf otel_env', label: 'remove otel_env file')

                    def playbooks = duffy_ssh("ls forklift/pipelines/${playBook['pipeline']}", 'duffy_box', './', true)
                    playbooks = playbooks.split("\n")

                    for(playbook in playbooks) {
                        stage(playbook) {
                            duffy_ssh("source otel_env && cd forklift && ansible-playbook pipelines/${playBook['pipeline']}/${playbook} ${extra_vars}", 'duffy_box', './')
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                extra_vars = buildExtraVars(extraVars: playBook['extraVars'])
                duffy_ssh("source otel_env && cd forklift && ansible-playbook playbooks/collect_debug.yml --limit '${playBook['boxes'].join(',')}' ${extra_vars}", 'duffy_box', './')
                runPlaybook(
                    playbook: 'jenkins-jobs/centos.org/ansible/fetch_debug_files.yml',
                    inventory: cico_inventory('./'),
                    extraVars: ["workspace": "${env.WORKSPACE}/debug"] + playBook['extraVars'],
                    commandLineExtraVars: true,
                    options: ['-b']
                  )
            }

            archiveArtifacts artifacts: 'debug/**/*.tap', allowEmptyArchive: true
            archiveArtifacts artifacts: 'debug/**/*.tar.xz', allowEmptyArchive: true
            archiveArtifacts artifacts: 'debug/**/*.xml', allowEmptyArchive: true
            archiveArtifacts artifacts: 'debug/**/report/**', allowEmptyArchive: true
            archiveArtifacts artifacts: 'debug/**/foreman-backup/**', allowEmptyArchive: true

            step([$class: "TapPublisher", testResults: "debug/**/*.tap"])
            junit testResults: "debug/**/*.xml", allowEmptyResults: true

        }

        cleanup {
            deprovision()
            deleteDir()
        }
    }
}
