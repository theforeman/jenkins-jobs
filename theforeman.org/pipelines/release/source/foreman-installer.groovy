pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '7'))
    }

    stages {
        stage("Collect Git Hash") {
            steps {
                git url: git_url, branch: git_ref
                script {
                    archive_git_hash()
                }
            }
        }
        stage("test ruby 2.7 & puppet 7") {
            steps {
                run_test(ruby: '2.7.6', puppet: '7')
            }
        }
        stage('Build and Archive Source') {
            steps {
                dir(project_name) {
                    git url: git_url, branch: git_ref
                }
                script {
                    sourcefile_paths = generate_sourcefiles(project_name: project_name, source_type: source_type)
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "${project_name} source release pipeline failed:", currentBuild.description)
        }

        cleanup {
            deleteDir()
        }
    }
}

def run_test(args) {
    def ruby = args.ruby
    def puppet = args.puppet

    withRuby(ruby, "PUPPET_VERSION='${puppet}' bundle install --without=development --jobs=5 --retry=5")
    archiveArtifacts(artifacts: 'Gemfile.lock')
    withRuby(ruby, "PUPPET_VERSION='${puppet}' bundle exec rake spec")
}
