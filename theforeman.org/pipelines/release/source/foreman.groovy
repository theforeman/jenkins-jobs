pipeline {
    agent { label 'fast' }

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '7'))
    }

    stages {
        stage('Test Matrix') {
            parallel {
                stage('ruby-2.7-postgres') {
                    agent { label 'fast' }
                    environment {
                        RUBY_VER = '2.7'
                        GEMSET = 'ruby2.7'
                    }
                    stages {
                        stage("setup-2.7-postgres") {
                            steps {
                                git url: git_url, branch: git_ref
                                script {
                                    archive_git_hash()
                                }
                                configureRVM(env.RUBY_VER, env.GEMSET)
                                databaseFile(gemset(env.GEMSET))
                                configureDatabase(env.RUBY_VER, env.GEMSET)
                            }
                        }
                        stage("unit-tests-2.7-postgres") {
                            steps {
                                withRVM(['bundle exec rake jenkins:unit TESTOPTS="-v" --trace'], env.RUBY_VER, env.GEMSET)
                            }
                        }
                    }
                    post {
                        always {
                            junit(testResults: 'jenkins/reports/unit/*.xml')
                        }
                        cleanup {
                            cleanup(env.RUBY_VER, env.GEMSET)
                            deleteDir()
                        }
                    }
                }
                stage('ruby-2.7-postgres-integrations') {
                    agent { label 'fast' }
                    environment {
                        RUBY_VER = '2.7'
                        GEMSET = 'ruby2.7-ui'
                    }
                    stages {
                        stage("setup-2.7-postgres-ui") {
                            steps {
                                git url: git_url, branch: git_ref
                                configureRVM(env.RUBY_VER, env.GEMSET)
                                databaseFile(gemset(env.GEMSET))
                                configureDatabase(env.RUBY_VER, env.GEMSET)
                                withRVM(['npm install --no-audit'], env.RUBY_VER, env.GEMSET)
                                archiveArtifacts(artifacts: 'package-lock.json')
                            }
                        }
                        stage("integration-tests-2.7-postgres-ui") {
                            steps {
                                withRVM(['bundle exec rake jenkins:integration TESTOPTS="-v" --trace'], env.RUBY_VER, env.GEMSET)
                            }
                        }
                    }
                    post {
                        always {
                            junit(testResults: 'jenkins/reports/unit/*.xml')
                        }
                        cleanup {
                            cleanup(env.RUBY_VER, env.GEMSET)
                            deleteDir()
                        }
                    }
                }
                stage('ruby-2.7-nulldb-assets') {
                    agent { label 'fast' }
                    environment {
                        RUBY_VER = '2.7'
                        GEMSET = 'ruby2.7-assets'
                    }
                    stages {
                        stage("setup-2.7-nulldb") {
                            steps {
                                git url: git_url, branch: git_ref
                                configureRVM(env.RUBY_VER, env.GEMSET)
                                withRVM(['bundle install --without=development --jobs=5 --retry=5'], env.RUBY_VER, env.GEMSET)
                                sh "cp db/schema.rb.nulldb db/schema.rb"
                                filter_package_json(env.RUBY_VER, env.GEMSET)
                                withRVM(['npm install --no-audit'], env.RUBY_VER, env.GEMSET)
                            }
                        }
                        stage("assets-precompile-2.7-nulldb") {
                            steps {
                                withRVM(['bundle exec rake assets:precompile RAILS_ENV=production DATABASE_URL=nulldb://nohost'], env.RUBY_VER, env.GEMSET)
                            }
                        }
                    }
                    post {
                        cleanup {
                            cleanup(env.RUBY_VER, env.GEMSET)
                            deleteDir()
                        }
                    }
                }
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
        success {
            build(
                job: "${project_name}-${git_ref}-package-release",
                propagate: false,
                wait: false
            )

        }

        failure {
            notifyDiscourse(env, "${project_name} source release pipeline failed:", currentBuild.description)
        }

        cleanup {
            deleteDir()
        }
    }
}
