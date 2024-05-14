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
                        RUBY_VER = '2.7.6'
                    }
                    stages {
                        stage("setup-2.7-postgres") {
                            steps {
                                git url: git_url, branch: git_ref
                                script {
                                    archive_git_hash()
                                }
                                databaseFile("${env.JOB_NAME}-${env.BUILD_ID}")
                                configureDatabase(env.RUBY_VER)
                            }
                        }
                        stage("unit-tests-2.7-postgres") {
                            steps {
                                bundleExec(env.RUBY_VER, 'rake jenkins:unit TESTOPTS="-v" --trace')
                            }
                        }
                    }
                    post {
                        always {
                            junit(testResults: 'jenkins/reports/unit/*.xml')
                        }
                        cleanup {
                            cleanup(env.RUBY_VER)
                            deleteDir()
                        }
                    }
                }
                stage('ruby-2.7-postgres-integrations') {
                    agent { label 'fast' }
                    environment {
                        RUBY_VER = '2.7.6'
                    }
                    stages {
                        stage("setup-2.7-postgres-ui") {
                            steps {
                                git url: git_url, branch: git_ref
                                databaseFile("${env.JOB_NAME}-${env.BUILD_ID}-ui")
                                configureDatabase(env.RUBY_VER)
                                withRuby(env.RUBY_VER, 'npm install --no-audit --legacy-peer-deps')
                                archiveArtifacts(artifacts: 'package-lock.json')
                            }
                        }
                        stage("integration-tests-2.7-postgres-ui") {
                            steps {
                                bundleExec(env.RUBY_VER, 'rake jenkins:integration TESTOPTS="-v" --trace')
                            }
                        }
                    }
                    post {
                        always {
                            junit(testResults: 'jenkins/reports/unit/*.xml')
                        }
                        cleanup {
                            cleanup(env.RUBY_VER)
                            deleteDir()
                        }
                    }
                }
                stage('ruby-2.7-nulldb-assets') {
                    agent { label 'fast' }
                    environment {
                        RUBY_VER = '2.7.6'
                    }
                    stages {
                        stage("setup-2.7-nulldb") {
                            steps {
                                git url: git_url, branch: git_ref
                                bundleInstall(env.RUBY_VER, '--without=development')
                                sh "cp db/schema.rb.nulldb db/schema.rb"
                                filter_package_json(env.RUBY_VER)
                                withRuby(env.RUBY_VER, 'npm install --no-audit --legacy-peer-deps')
                            }
                        }
                        stage("assets-precompile-2.7-nulldb") {
                            steps {
                                bundleExec(env.RUBY_VER, 'rake assets:precompile RAILS_ENV=production DATABASE_URL=nulldb://nohost')
                            }
                        }
                    }
                    post {
                        cleanup {
                            cleanup(env.RUBY_VER)
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
