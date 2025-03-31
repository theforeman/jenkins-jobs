RUBY_VERSION = ruby_version

pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        ansiColor('xterm')
    }

    environment {
        BUNDLE_WITHOUT = 'development'
        TESTOPTS = '-v'
    }

    stages {
        stage('test') {
            matrix {
                agent { label 'fast' }
                axes {
                    axis {
                        name 'RAKE_TASK'
                        values 'jenkins:unit', 'jenkins:integration', 'assets:precompile'
                    }
                }
                environment {
                    RAILS_ENV = railsEnvForTask(RAKE_TASK)
                    DATABASE_URL = databaseUrlForTask(RAKE_TASK)
                }
                stages {
                    stage('setup') {
                        steps {
                            git branch: git_branch, url: 'https://github.com/theforeman/foreman'
                            bundleInstall(RUBY_VERSION)
                            archiveArtifacts(artifacts: 'Gemfile.lock')
                            script {
                                if (RAKE_TASK == 'assets:precompile') {
                                    sh "cp db/schema.rb.nulldb db/schema.rb"
                                    filter_package_json(RUBY_VERSION)
                                }
                                if (RAKE_TASK == 'jenkins:integration' || RAKE_TASK == 'assets:precompile' ){
                                    withRuby(RUBY_VERSION, 'npm install --no-audit --legacy-peer-deps')
                                    archiveArtifacts(artifacts: 'package-lock.json')
                                }
                            }
                        }
                    }
                    stage('database') {
                        when {
                            expression { RAKE_TASK != 'assets:precompile' }
                        }
                        steps {
                            bundleExec(RUBY_VERSION, "rake db:create --trace")
                            bundleExec(RUBY_VERSION, "rake db:migrate --trace")
                        }
                    }
                    stage('rake task') {
                        steps {
                            bundleExec(RUBY_VERSION, "rake ${RAKE_TASK} --trace")
                        }
                    }
                }
                post {
                    always {
                        junit(testResults: 'jenkins/reports/*/*.xml', allowEmptyResults: RAKE_TASK == 'assets:precompile')
                    }
                    cleanup {
                        bundleExec(RUBY_VERSION, 'rake db:drop DISABLE_DATABASE_ENVIRONMENT_CHECK=true')
                        deleteDir()
                    }
                }
            }
        }
    }
}
