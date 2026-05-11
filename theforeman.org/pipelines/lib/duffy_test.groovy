def pipelineVars(Map args) {
    def box_prefix = "pipe-"
    if (args.action == 'upgrade') {
        box_prefix = "pipe-up-"
    }
    def boxes = ["${box_prefix}${args.type}-*${args.version}-${args.os}"]

    def extra_vars = [
        'pipeline_version': args.version,
        'pipeline_os': args.os,
        'pipeline_type': args.type,
        'pipeline_action': args.action
    ]

    if (args.extra_vars != null) {
        extra_vars.putAll(args.extra_vars)
    }

    return [
        'boxes': boxes,
        'pipeline': args.action,
        'extraVars': extra_vars
    ]
}

def runDuffyTest(Map args) {
    def test_pipelines = args.pipelines
    def test_type = args.type
    def test_version = args.version
    def expected_version = args.expected_version ?: ''

    node('el') {
        timestamps {
            ansiColor('xterm') {
                withEnv([
                    'ANSIBLE_CALLBACKS_ENABLED=community.general.opentelemetry',
                    'ANSIBLE_INVENTORY_ENABLED=evgeni.duffy.inventory'
                ]) {
                    try {
                        stage("Setup Environment [${test_type}-${test_version}]") {
                            deleteDir()
                            git url: 'https://github.com/theforeman/forklift.git', poll: false

                            sh(label: 'pip install', script: 'pip3.12 install --user opentelemetry-api opentelemetry-sdk opentelemetry-exporter-otlp duffy[client]')

                            setupDuffyClient()
                        }

                        stage("Provision Node [${test_type}-${test_version}]") {
                            provisionDuffy()
                        }

                        stage("Install Pipeline Requirements [${test_type}-${test_version}]") {
                            def duffy_session = readFile(file: 'jenkins-jobs/theforeman.org/ansible/duffy_session')

                            def pipeline_users = []
                            test_pipelines.each { action, oses ->
                                oses.each { os ->
                                    pipeline_users.push("pipe-${os}-${action}")
                                }
                            }
                            runPlaybook(
                                playbook: 'playbooks/setup_pipeline_users.yml',
                                inventory: duffy_inventory('./'),
                                limit: "duffy_session_${duffy_session}",
                                options: ['-b'],
                                extraVars: ['pipeline_users': pipeline_users],
                            )

                            def setup_extra_vars
                            if (test_type == 'pulpcore') {
                                setup_extra_vars = ['forklift_install_pulp_from_galaxy': true, 'forklift_install_from_galaxy': false, 'pipeline_version': test_version]
                            } else {
                                setup_extra_vars = ['forklift_telemetry': true]
                            }
                            pipeline_users.each { user ->
                                runPlaybook(
                                    playbook: 'playbooks/setup_forklift.yml',
                                    inventory: duffy_inventory('./'),
                                    limit: "duffy_session_${duffy_session}",
                                    remote_user: user,
                                    extraVars: setup_extra_vars,
                                    commandLineExtraVars: true,
                                )
                            }
                        }

                        stage("Run Pipelines [${test_type}-${test_version}]") {
                            def otel_env = """
                            export OTEL_EXPORTER_OTLP_HEADERS='${env.OTEL_EXPORTER_OTLP_HEADERS}'
                            export OTEL_EXPORTER_OTLP_ENDPOINT='${env.OTEL_EXPORTER_OTLP_ENDPOINT}'
                            export OTEL_TRACES_EXPORTER='${env.OTEL_TRACES_EXPORTER}'
                            export TRACE_ID='${env.TRACE_ID}'
                            export SPAN_ID='${env.SPAN_ID}'
                            export TRACEPARENT='${env.TRACEPARENT}'
                            export TRACESTATE='${env.TRACESTATE}'
                            """

                            writeFile(file: 'otel_env', text: otel_env)

                            def branches = [:]
                            test_pipelines.each { action, oses ->
                                oses.each { os ->
                                    def name = "${os}-${action}"
                                    def username = "pipe-${os}-${action}"
                                    def boxname = "${username}@duffy_box"
                                    branches[name] = {
                                        def playBook = pipelineVars(action: action, type: test_type, version: test_version, os: os, extra_vars: ['foreman_expected_version': expected_version])
                                        def extra_vars = buildExtraVars(extraVars: playBook['extraVars'])
                                        def playbooks = duffy_ssh("ls forklift/pipelines/${playBook['pipeline']}", boxname, './', true)
                                        playbooks = playbooks.split("\n")

                                        duffy_scp_in('otel_env', 'otel_env', boxname, './')
                                        for (playbook in playbooks) {
                                            duffy_ssh("source otel_env && cd forklift && ansible-playbook pipelines/${playBook['pipeline']}/${playbook} ${extra_vars}", boxname, './')
                                        }
                                    }
                                }
                            }
                            parallel branches
                        }
                    } finally {
                        stage("Collect Results [${test_type}-${test_version}]") {
                            def duffy_session = readFile(file: 'jenkins-jobs/theforeman.org/ansible/duffy_session')
                            def branches = [:]
                            test_pipelines.each { action, oses ->
                                oses.each { os ->
                                    def name = "${os}-${action}-post"
                                    def username = "pipe-${os}-${action}"
                                    def boxname = "${username}@duffy_box"
                                    branches[name] = {
                                        def playBook = pipelineVars(action: action, type: test_type, version: test_version, os: os, extra_vars: ['foreman_expected_version': expected_version])
                                        def extra_vars = buildExtraVars(extraVars: playBook['extraVars'])
                                        try {
                                            duffy_ssh("source otel_env && cd forklift && ansible-playbook playbooks/collect_debug.yml --limit '${playBook['boxes'].join(',')}' ${extra_vars}", boxname, './')
                                            runPlaybook(
                                                playbook: 'jenkins-jobs/theforeman.org/ansible/fetch_debug_files.yml',
                                                inventory: duffy_inventory('./'),
                                                limit: "duffy_session_${duffy_session}",
                                                extraVars: ["workspace": "${env.WORKSPACE}/debug"] + playBook['extraVars'],
                                                commandLineExtraVars: true,
                                                remote_user: username,
                                                options: ['-b']
                                            )
                                        } catch (Exception ex) {
                                            echo "Exception: ${ex}"
                                        }
                                    }
                                }
                            }
                            parallel branches

                            archiveArtifacts artifacts: 'debug/**/*.tap', allowEmptyArchive: true
                            archiveArtifacts artifacts: 'debug/**/*.tar.xz', allowEmptyArchive: true
                            archiveArtifacts artifacts: 'debug/**/*.xml', allowEmptyArchive: true
                            archiveArtifacts artifacts: 'debug/**/report.tar*', allowEmptyArchive: true
                            archiveArtifacts artifacts: 'debug/**/foreman-backup/**', allowEmptyArchive: true

                            step([$class: "TapPublisher", testResults: "debug/**/*.tap", failIfNoResults: false])
                            junit testResults: "debug/**/*.xml", allowEmptyResults: true
                        }

                        deprovisionDuffy()
                        deleteDir()
                    }
                }
            }
        }
    }
}
