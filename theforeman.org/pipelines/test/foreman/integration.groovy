//def ruby_versions = foreman_integration_versions[ghprbTargetBranch]['ruby']
def ruby_versions = foreman_integration_versions['develop']['ruby']

pipeline {
    agent { label 'fast' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        ansiColor('xterm')
    }

    stages {
        stage('matrix') {
            matrix {
                agent { label 'fast' }
                axes {
                    axis {
                        name 'ruby'
                        values '2.7'
                    }
                }
                when {
                    expression {
                        ruby_versions.contains(ruby)
                    }
                }
                stages {
                    stage('repository') {
                        steps {
                            ghprb_git_checkout()
                        }
                    }
                    stage('rvm') {
                        steps {
                            configureRVM(ruby)
                        }
                    }
                    stage('database') {
                        steps {
                            databaseFile(gemset())
                            configureDatabase(ruby)
                        }
                    }
                    stage('npm') {
                        steps {
                            withRVM(["bundle exec npm install --package-lock-only --no-audit"], ruby)
                            withRVM(["bundle exec npm ci --no-audit"], ruby)
                        }
                    }
                    stage("integration-tests") {
                        steps {
                            withRVM(['bundle exec rake jenkins:integration TESTOPTS="-v" --trace'], ruby)
                        }
                    }
                    stage("assets:precompile") {
                        steps {
                            sh "cp db/schema.rb.nulldb db/schema.rb"
                            withRVM(['bundle exec rake assets:precompile RAILS_ENV=production DATABASE_URL=nulldb://nohost'], ruby)
                        }
                    }
                }
                post {
                    always {
                        junit(testResults: 'jenkins/reports/unit/*.xml')
                    }
                    cleanup {
                        cleanup(ruby)
                        deleteDir()
                    }
                }
            }
        }
    }
}
