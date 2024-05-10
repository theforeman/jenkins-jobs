pipeline {
    agent { label 'el' }

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

                virtEnv('jjb-venv', 'pip install pbr', '3.11')
                virtEnv('jjb-venv', 'pip install -r requirements.txt', '3.11')
            }
        }

        stage('Update ci.theforeman.org jobs') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'theforeman-jenkins', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    virtEnv('jjb-venv', "cd ./theforeman.org && REQUESTS_CA_BUNDLE=/etc/pki/tls/cert.pem jenkins-jobs --conf ./foreman_jenkins.ini --user ${env.USERNAME} --password '${env.PASSWORD}' update --delete-old -r .", '3.11')
                }
            }
        }

        stage('Update jenkins-foreman.apps.ocp.cloud.ci.centos.org jobs') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'centos-jenkins-openshift-cloud', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    virtEnv('jjb-venv', "cd ./centos.org && REQUESTS_CA_BUNDLE=/etc/pki/tls/cert.pem jenkins-jobs --conf ./centos_jenkins.ini --user ${env.USERNAME} --password '${env.PASSWORD}' update --delete-old -r ./jobs", '3.11')
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
