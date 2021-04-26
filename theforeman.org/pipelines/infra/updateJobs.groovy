pipeline {
    agent { label 'fast' }

    stages {
        stage('Setup workspace') {
            steps {
                checkout([
                    $class : 'GitSCM',
                    branches : [[name: 'master']],
                    extensions: [[$class: 'CleanCheckout']],
                    userRemoteConfigs: [
                        [url: 'https://github.com/theforeman/jenkins-jobs.git']
                    ]
                ])

                virtEnv('jjb-venv', 'pip install pbr')
                virtEnv('jjb-venv', 'pip install jenkins-job-builder')
            }
        }

        stage('Update ci.theforeman.org jobs') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'theforeman-jenkins', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    virtEnv('jjb-venv', "cd ./theforeman.org && jenkins-jobs --conf ./foreman_jenkins.ini --user ${env.USERNAME} --password '${env.PASSWORD}' update --delete-old -r .")
                }
            }
        }

        stage('Update ci.centos.org jobs') {
            steps {
                withCredentials([string(credentialsId: 'centos-jenkins', variable: 'PASSWORD')]) {
                    virtEnv('jjb-venv', "cd ./centos.org && jenkins-jobs --conf ./centos_jenkins.ini --user 'foreman' --password '${env.PASSWORD}' update --delete-old -r ./jobs")
                }
            }
        }
    }

    post {
        always {
            script {
                if(fileExists('update_jobs')) {
                    dir('update_jobs') {
                        deleteDir()
                    }
                }
            }
        }
    }
}
