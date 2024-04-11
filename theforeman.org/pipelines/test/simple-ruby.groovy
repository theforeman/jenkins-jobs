def ruby = '2.7.6'

pipeline {
    agent { label 'el8' }
    options {
        timeout(time: 1, unit: 'HOURS')
        ansiColor('xterm')
    }

    stages {
        stage('Setup Git Repos') {
            steps {
                git url: 'https://github.com/theforeman/foreman-installer', branch: 'develop'
                sh "cp Gemfile Gemfile.${ruby}"
            }
        }
        stage("bundle-install") {
            steps {
                bundleInstall(ruby, "Gemfile.${ruby}")
            }
        }
        stage('Run Rubocop') {
            steps {
                bundleExec(ruby, "rake rubocop TESTOPTS='-v' --trace", "Gemfile.${ruby}")
            }
        }
        stage('Run Tests') {
            steps {
                bundleExec(ruby, "rake spec TESTOPTS='-v' --trace", "Gemfile.${ruby}")
            }
        }
    }
    post {
        always {
            deleteDir()
        }
    }
}
