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

                sh(label: 'pip install', script: 'pip3.8 install --user opentelemetry-api opentelemetry-sdk opentelemetry-exporter-otlp')

                setupDuffyClient()
            }
        }
        stage('Provision Node') {
            steps {
                provisionDuffy()
            }
        }
        stage('Install Pipeline Requirements') {
            steps {
                script {
                    pipeline_users = []
                    pipelines.each { action, oses ->
                        oses.each { os ->
                            pipeline_users.push("pipe-${os}-${action}")
                        }
                    }
                    runPlaybook(
                        playbook: 'playbooks/setup_pipeline_users.yml',
                        inventory: duffy_inventory('./'),
                        options: ['-b'],
                        extraVars: ['pipeline_users': pipeline_users],
                    )

                    if (params.type == 'pulpcore') {
                        setup_extra_vars = ['forklift_install_pulp_from_galaxy': true, 'forklift_install_from_galaxy': false, 'pipeline_version': params.version]
                    } else {
                        setup_extra_vars = ['forklift_telemetry': true]
                    }
                    pipeline_users.each { user ->
                        runPlaybook(
                            playbook: 'playbooks/setup_forklift.yml',
                            inventory: duffy_inventory('./'),
                            remote_user: user,
                            extraVars: setup_extra_vars,
                            commandLineExtraVars: true,
                        )
                    }
                }
            }
        }
        stage('Run Pipelines') {
            steps {
                script {
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

                    def branches = [:]
                    pipelines.each { action, oses ->
                        oses.each { os ->
                            def name = "${os}-${action}"
                            def username = "pipe-${os}-${action}"
                            def boxname = "${username}@duffy_box"
                            branches[name] = {
                                def playBook = pipelineVars(action: action, type: params.type, version: params.version, os: os, extra_vars: ['expected_version': params.expected_version ?: ''])
                                def extra_vars = buildExtraVars(extraVars: playBook['extraVars'])
                                def playbooks = duffy_ssh("ls forklift/pipelines/${playBook['pipeline']}", boxname, './', true)
                                playbooks = playbooks.split("\n")

                                duffy_scp_in('otel_env', 'otel_env', boxname, './')
                                for(playbook in playbooks) {
                                    duffy_ssh("source otel_env && cd forklift && ansible-playbook pipelines/${playBook['pipeline']}/${playbook} ${extra_vars}", boxname, './')
                                }
                            }
                        }
                    }
                    parallel branches

                }
            }
        }
    }

    post {
        always {
            script {
                def branches = [:]
                pipelines.each { action, oses ->
                    oses.each { os ->
                        def name = "${os}-${action}-post"
                        def username = "pipe-${os}-${action}"
                        def boxname = "${username}@duffy_box"
                        branches[name] = {
                            def playBook = pipelineVars(action: action, type: params.type, version: params.version, os: os, extra_vars: ['expected_version': params.expected_version ?: ''])
                            def extra_vars = buildExtraVars(extraVars: playBook['extraVars'])
                            try {
                                duffy_ssh("source otel_env && cd forklift && ansible-playbook playbooks/collect_debug.yml --limit '${playBook['boxes'].join(',')}' ${extra_vars}", boxname, './')
                                runPlaybook(
                                    playbook: 'jenkins-jobs/centos.org/ansible/fetch_debug_files.yml',
                                    inventory: duffy_inventory('./'),
                                    extraVars: ["workspace": "${env.WORKSPACE}/debug"] + playBook['extraVars'],
                                    commandLineExtraVars: true,
                                    remote_user: username,
                                    options: ['-b']
                                )
                            } catch(Exception ex) {
                                echo "Exception: ${ex}"
                            }
                        }
                    }
                }
                parallel branches
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
            deprovisionDuffy()
            deleteDir()
        }
    }
}
