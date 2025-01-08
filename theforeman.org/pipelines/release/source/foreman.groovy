pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '7'))
    }

    environment {
        RUBY_VERSION = '2.7.6'
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
                            git url: git_url, branch: git_ref
                            script {
                                archive_git_hash()
                            }
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
        stage('Build and Archive Source') {
            agent any

            steps {
                dir(project_name) {
                    git url: git_url, branch: git_ref
                }
                script {
                    sourcefile_paths = generate_sourcefiles(project_name: project_name, source_type: source_type, ruby_version: RUBY_VERSION)
                }
            }

            post {
                cleanup {
                    deleteDir()
                }
            }
        }
    }

    post {
        failure {
            notifyDiscourse(env, "${project_name} source release pipeline failed:", currentBuild.description)
        }
    }
}
