pipeline {
    options {
        timestamps()
        timeout(time: 3, unit: 'HOURS')
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '7'))
    }

    agent { label 'fast' }

    stages {
        stage('Setup Git Repos') {
            steps {
                deleteDir()
                git url: git_url, branch: git_ref
                script {
                    archive_git_hash()
                }

                dir('foreman') {
                   git url: "https://github.com/theforeman/foreman", branch: 'develop', poll: false, changelog: false
                }
            }
        }
        stage('Configure Environment') {
            steps {

                dir('foreman') {
                    addGem()
                    databaseFile("${env.JOB_NAME}-${env.BUILD_ID}")
                }

            }
        }
        stage('Configure Database') {
            steps {

                dir('foreman') {
                    configureDatabase(ruby)
                }

            }
        }
        stage('Install Foreman npm packages') {
            steps {
                dir('foreman') {
                    bundleExec(ruby, "npm install --package-lock-only --no-audit --legacy-peer-deps")
                    bundleExec(ruby, "npm ci --no-audit --legacy-peer-deps")
                }
            }
        }
        stage('Run Tests') {
            parallel {
                stage('tests') {
                    steps {
                        dir('foreman') {
                            bundleExec(ruby, 'rake jenkins:katello TESTOPTS="-v" --trace')
                        }
                    }
                }
                stage('rubocop') {
                    steps {
                        bundleInstall(ruby)
                        bundleExec(ruby, 'rubocop --parallel')
                    }
                }
                stage('react-ui') {
                    when {
                        expression { fileExists('package.json') }
                    }
                    steps {
                        sh(script: "npm install --package-lock-only --no-audit --legacy-peer-deps", label: "npm install --package-lock-only")
                        archiveArtifacts(artifacts: 'package-lock.json')
                        sh(script: "npm ci --no-audit --legacy-peer-deps", label: "npm ci")
                        sh(script: 'JEST_TIMEOUT=300000 npm test', label: "npm test")
                    }
                }
                stage('angular-ui') {
                    steps {
                        script {
                            dir('engines/bastion') {
                                sh(script: "npm install --package-lock-only --no-audit --legacy-peer-deps", label: "npm install --package-lock-only")
                                sh(script: "npm ci --no-audit --legacy-peer-deps", label: "npm ci")
                                sh(script: "grunt ci", label: "grunt ci")
                            }
                            dir('engines/bastion_katello') {
                                sh(script: "npm install --package-lock-only --no-audit --legacy-peer-deps", label: "npm install --package-lock-only")
                                sh(script: "npm ci --no-audit --legacy-peer-deps", label: "npm ci")
                                sh(script: "grunt ci", label: "grunt ci")
                            }
                        }
                    }
                }
                stage('assets-precompile') {
                    steps {
                        dir('foreman') {
                            bundleExec(ruby, "rake plugin:assets:precompile[${project_name}] RAILS_ENV=production --trace")
                        }
                    }
                }
            }
            post {
                always {
                    dir('foreman') {
                        archiveArtifacts artifacts: "log/test.log"
                        junit keepLongStdio: true, testResults: 'jenkins/reports/unit/*.xml'
                    }
                }
            }
        }
        stage('Test db:seed') {
            steps {

                dir('foreman') {

                    bundleExec(ruby, 'rake db:drop RAILS_ENV=test >/dev/null 2>/dev/null || true')
                    bundleExec(ruby, 'rake db:create RAILS_ENV=test')
                    bundleExec(ruby, 'rake db:migrate RAILS_ENV=test')
                    bundleExec(ruby, 'rake db:seed RAILS_ENV=test')

                }

            }
        }
        stage('Build and Archive Source') {
            steps {
                dir(project_name) {
                    git url: git_url, branch: git_ref
                }
                script {
                    generate_sourcefiles(project_name: project_name, source_type: source_type)
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
            dir('foreman') {
                cleanup(ruby)
            }
            deleteDir()
        }
    }
}
