pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
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
                stages {
                    stage("Clone repository") {
                        steps {
                            git url: git_url, branch: git_ref
                        }
                    }
                    stage("Test Ruby") {
                        environment {
                            // ci_reporters gem
                            CI_REPORTS = 'jenkins/reports/unit'
                            // minitest-reporters
                            MINITEST_REPORTER = 'JUnitReporter'
                            MINITEST_REPORTERS_REPORTS_DIR = 'jenkins/reports/unit'
                        }
                        steps {
                            run_test(ruby: env.ruby)
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

def run_test(args) {
    def ruby = args.ruby

    try {
        sh "cp config/settings.yml.example config/settings.yml"
        bundleInstall(ruby, "--without=development")
        archiveArtifacts(artifacts: 'Gemfile.lock')
        bundleExec(ruby, "rake jenkins:unit --trace")
    } finally {
        junit(testResults: 'jenkins/reports/unit/*.xml')
    }
}
