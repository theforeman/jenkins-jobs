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
                        values '2.7', '3.0'
                    }
                }
                stages {
                    stage("clone repository") {
                        steps {
                            git url: git_url, branch: git_ref
                        }
                    }
                    stage("unit test") {
                        steps {
                            configureRVM(env.ruby, project_name)
                            withRVM(["bundle install --with=development --jobs=5 --retry=5"], env.ruby, project_name)
                            archiveArtifacts(artifacts: 'Gemfile.lock')
                            withRVM(["bundle exec rake jenkins:unit --trace"], env.ruby, project_name)
                        }
                    }
                }
                post {
                    cleanup {
                        cleanupRVM(env.ruby, project_name)
                        junit(testResults: 'jenkins/reports/unit/*.xml')
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

        //failure {
            //notifyDiscourse(env, "${project_name} source release pipeline failed:", currentBuild.description)
        //}

        cleanup {
            deleteDir()
        }
    }
}
