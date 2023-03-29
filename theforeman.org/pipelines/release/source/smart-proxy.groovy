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
                        values '2.7', '3.0', '3.1'
                    }
                }
                stages {
                    stage("Clone repository") {
                        steps {
                            git url: git_url, branch: git_ref
                        }
                    }
                    stage("Test Ruby") {
                        steps {
                            run_test(env.ruby)
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
                    archive_git_hash()
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
    def gemset = "ruby-${ruby}"

    try {
        configureRVM(ruby, gemset)
        withRVM(["cp config/settings.yml.example config/settings.yml"], ruby, gemset)
        withRVM(["bundle install --without=development --jobs=5 --retry=5"], ruby, gemset)
        archiveArtifacts(artifacts: 'Gemfile.lock')
        withRVM(["bundle exec rake jenkins:unit --trace"], ruby, gemset)
    } finally {
        cleanupRVM(ruby, gemset)
        junit(testResults: 'jenkins/reports/unit/*.xml')
    }
}
