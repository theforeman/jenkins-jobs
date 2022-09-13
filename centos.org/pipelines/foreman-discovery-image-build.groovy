pipeline {
    agent { label 'cico-workspace' }

    environment {
        proxy_repository = env.getProperty('proxy_repository')
        branch = env.getProperty('branch')
        repo_owner = env.getProperty('repo_owner')
    }

    stages {
        stage('Setup Environment') {
            steps {
                deleteDir()
                git url: 'https://github.com/theforeman/forklift.git'

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
                    def duffy_session = readFile(file: 'jenkins-jobs/centos.org/ansible/duffy_session')

                    runPlaybook(
                        playbook: 'playbooks/setup_pipeline_users.yml',
                        inventory: duffy_inventory('./'),
                        limit: "duffy_session_${duffy_session}",
                        options: ['-b'],
                        extraVars: ['pipeline_users': ['pipe-fdi-builder']],
                    )
                    runPlaybook(
                        playbook: 'playbooks/setup_forklift.yml',
                        inventory: duffy_inventory('./'),
                        limit: "duffy_session_${duffy_session}",
                        remote_user: 'pipe-fdi-builder',
                        extraVars: ['vagrant_scp': true],
                        commandLineExtraVars: true,
                    )
                }
            }
        }
        stage('Run Build') {
            steps {
                script {
                    def boxname = 'pipe-fdi-builder@duffy_box'
                    duffy_ssh("git clone https://github.com/${env.repo_owner}/foreman-discovery-image/ --branch ${env.branch}", boxname, './')
                    duffy_ssh("cd foreman-discovery-image/aux/vagrant-build/ && repoowner='${env.repo_owner}' branch='${env.branch}' proxy_repo='${env.proxy_repository}' vagrant up fdi-builder", boxname, './')
                    duffy_ssh("cd foreman-discovery-image/aux/vagrant-build/ && vagrant ssh -c \"sudo chmod +rx /root\" fdi-builder", boxname, './')
                    duffy_ssh("cd foreman-discovery-image/aux/vagrant-build/ && vagrant scp fdi-builder:foreman-discovery-image/ ./result", boxname, './')
                    duffy_scp('foreman-discovery-image/aux/vagrant-build/result/', '.', boxname, './')
                }
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'result/fdi*tar', allowEmptyArchive: true
            archiveArtifacts artifacts: 'result/fdi*iso', allowEmptyArchive: true
            archiveArtifacts artifacts: 'result/*log', allowEmptyArchive: true
        }

        cleanup {
            deprovisionDuffy()
            deleteDir()
        }
    }
}
