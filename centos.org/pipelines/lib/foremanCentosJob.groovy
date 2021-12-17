playBook = pipelineVars(action: params.action, type: params.type, version: params.version, os: params.distro, extra_vars: ['foreman_expected_version': params.expected_version ?: ''])

pipeline {
    agent { label 'foreman' }

    options {
        timestamps()
        ansiColor('xterm')
        buildDiscarder(logRotator(numToKeepStr: '3'))
    }

    stages {
        stage('Setup Environment') {
            steps {
                deleteDir()
                git url: 'https://github.com/theforeman/forklift.git'
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
                        setup_extra_vars = ['forklift_install_pulp_from_galaxy': true, 'forklift_install_from_galaxy': false]
                    } else {
                        setup_extra_vars = []
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

                    def playbooks = duffy_ssh("ls forklift/pipelines/${playBook['pipeline']}", 'duffy_box', './', true)
                    playbooks = playbooks.split("\n")

                    for(playbook in playbooks) {
                        stage(playbook) {
                            duffy_ssh("cd forklift && ansible-playbook pipelines/${playBook['pipeline']}/${playbook} ${extra_vars}", 'duffy_box', './')
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
                duffy_ssh("cd forklift && ansible-playbook playbooks/collect_debug.yml --limit '${playBook['boxes'].join(',')}' ${extra_vars}", 'duffy_box', './')
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
