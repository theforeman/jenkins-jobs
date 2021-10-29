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
            }
        }
        stage('Provision Node') {
            steps {
                provision()
            }
        }
        stage('Install Pipeline Requirements') {
            steps {
                runPlaybook(
                    playbook: 'playbooks/setup_forklift.yml',
                    inventory: cico_inventory('./'),
                    extraVars: ['vagrant_scp': true],
                    commandLineExtraVars: true,
                )
            }
        }
        stage('Run Build') {
            steps {
                script {
                    duffy_ssh("git clone https://github.com/${env.repo_owner}/foreman-discovery-image/ --branch ${env.branch}", 'duffy_box', './')
                    duffy_ssh("cd foreman-discovery-image/aux/vagrant-build/ && repoowner='${env.repo_owner}' branch='${env.branch}' proxy_repo='${env.proxy_repository}' vagrant up fdi-builder", 'duffy_box', './')
                    duffy_ssh("cd foreman-discovery-image/aux/vagrant-build/ && vagrant ssh -c \"sudo chmod +rx /root\" fdi-builder", 'duffy_box', './')
                    duffy_ssh("cd foreman-discovery-image/aux/vagrant-build/ && vagrant scp fdi-builder:foreman-discovery-image/ ./result", 'duffy_box', './')
                    duffy_scp('foreman-discovery-image/aux/vagrant-build/result/', '.', 'duffy_box', './')
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
            deprovision()
            deleteDir()
        }
    }
}
