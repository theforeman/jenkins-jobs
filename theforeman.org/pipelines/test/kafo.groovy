pipeline {
    agent none
    options {
        timeout(time: 1, unit: 'HOURS')
    }

    stages {
        stage('Test') {
            matrix {
                agent any
                axes {
                    axis {
                        name 'ruby'
                        values '2.4', '2.5', '2.7'
                    }
                    axis {
                        name 'PUPPET_VERSION'
                        values '5.0', '6.0', '7.0'
                    }
                }
                excludes {
                    exclude {
                        axis {
                            name 'ruby'
                            notValues '2.4'
                        }
                        axis {
                            name 'PUPPET_VERSION'
                            values '5.0'
                        }
                    }
                    exclude {
                        axis {
                            name 'ruby'
                            notValues '2.5'
                        }
                        axis {
                            name 'PUPPET_VERSION'
                            values '6.0'
                        }
                    }
                    exclude {
                        axis {
                            name 'ruby'
                            notValues '2.7'
                        }
                        axis {
                            name 'PUPPET_VERSION'
                            values '7.0'
                        }
                    }
                }
                stages {
                    stage('Setup Git Repos') {
                        steps {
                            ghprb_git_checkout()
                            sh "cp Gemfile Gemfile.${ruby}-${PUPPET_VERSION}"
                        }
                    }
                    stage("Setup RVM") {
                        steps {
                            // Bundler 2.4 dropped support for Ruby 2.5
                            configureRVM(ruby, "${ruby}-${PUPPET_VERSION}", '< 2.4')
                        }
                    }
                    stage('Install dependencies') {
                        steps {
                            withRVM(["bundle install --gemfile=Gemfile.${ruby}-${PUPPET_VERSION}"], ruby, "${ruby}-${PUPPET_VERSION}")
                        }
                    }
                    stage('Run Tests') {
                        steps {
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec rake jenkins:unit TESTOPTS='-v' --trace"], ruby, "${ruby}-${PUPPET_VERSION}")
                        }
                    }
                }
                post {
                    always {
                        archiveArtifacts artifacts: "Gemfile*lock"
                        junit keepLongStdio: true, testResults: 'jenkins/reports/unit/*.xml'
                        cleanupRVM(ruby, "${ruby}-${PUPPET_VERSION}")
                        deleteDir()
                    }
                }
            }
        }
    }
}
