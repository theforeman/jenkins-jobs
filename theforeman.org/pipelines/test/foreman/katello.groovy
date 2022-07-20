def ruby_versions = foreman_integration_versions[ghprbTargetBranch]['ruby']
def katello_branch = foreman_integration_versions[ghprbTargetBranch]['katello']

pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 3, unit: 'HOURS')
        ansiColor('xterm')
    }

    stages {
        stage('Test') {
            matrix {
                agent { label 'fast' }
                axes {
                    axis {
                        name 'ruby'
                        values '2.7'
                    }
                }
                when {
                    expression {
                        ruby_versions.contains(env.ruby)
                    }
                }
                stages {
                    stage('Setup Git Repos') {
                        steps {
                            git url: "https://github.com/Katello/katello", branch: katello_branch, poll: false

                            dir('foreman') {
                                ghprb_git_checkout()
                            }
                        }
                    }
                    stage("Setup RVM") {
                        steps {
                            configureRVM(ruby)
                        }
                    }
                    stage('Configure Environment') {
                        steps {
                            dir('foreman') {
                                addGem()
                                databaseFile(gemset())
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
                    stage('rubocop') {
                        steps {
                            dir('foreman') {
                                withRVM(['bundle exec rake katello:rubocop TESTOPTS="-v" --trace'], ruby)
                            }
                        }
                    }
                    stage('tests') {
                        steps {
                            dir('foreman') {
                                withRVM(['bundle exec rake jenkins:katello TESTOPTS="-v" --trace'], ruby)
                            }
                        }
                    }
                    stage('Test db:seed') {
                        steps {

                            dir('foreman') {

                                withRVM(['bundle exec rake db:drop RAILS_ENV=test >/dev/null 2>/dev/null || true'], ruby)
                                withRVM(['bundle exec rake db:create RAILS_ENV=test'], ruby)
                                withRVM(['bundle exec rake db:migrate RAILS_ENV=test'], ruby)
                                withRVM(['bundle exec rake db:seed RAILS_ENV=test'], ruby)

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
                    cleanup {
                        dir('foreman') {
                            cleanup(ruby)
                        }
                        deleteDir()
                    }
                }
            }
        }
    }

}
