pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '7'))
    }

    stages {
        stage('Test') {
            matrix {
                agent any
                axes {
                    axis {
                        name 'ruby'
                        values '2.7.6', '3.0.4', '3.1.0'
                    }
                }
                environment {
                    BUNDLE_WITHOUT = 'development'
                }
                stages {
                    stage("Clone repository") {
                        steps {
                            git url: git_url, branch: git_ref
                        }
                    }
                    stage('Install dependencies') {
                        steps {
                            bundleInstall(ruby)
                            archiveArtifacts(artifacts: 'Gemfile.lock')
                        }
                    }
                    stage('Run tests') {
                        environment {
                            // ci_reporters gem
                            CI_REPORTS = 'jenkins/reports/unit'
                            // minitest-reporters
                            MINITEST_REPORTER = 'JUnitReporter'
                            MINITEST_REPORTERS_REPORTS_DIR = 'jenkins/reports/unit'
                        }
                        steps {
                            bundleExec(ruby, 'rake jenkins:unit')
                        }
                        post {
                            always {
                                junit testResults: 'jenkins/reports/unit/*.xml'
                            }
                        }
                    }
                }
                post {
                    always {
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

                    script {
                        archive_git_hash()
                    }
                }

                script {
                    sourcefile_paths = generate_sourcefiles(project_name: project_name, source_type: source_type)
                }
            }
            post {
                always {
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
