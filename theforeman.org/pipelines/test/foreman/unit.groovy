//def ruby_versions = foreman_unit_versions[ghprbTargetBranch]['ruby']
def ruby_versions = foreman_unit_versions['develop']['ruby']

pipeline {
    agent none

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
                        values '2.7', '2.5'
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
                    stage("unit-tests") {
                        steps {
                            withRVM(['bundle exec rake jenkins:unit TESTOPTS="-v" --trace'], ruby)
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
