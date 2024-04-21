def git_checkout = {
    if (env.ghprbPullId) {
        ghprb_git_checkout()
    } else {
        git branch: "${params.BRANCH}", url: 'https://github.com/theforeman/smart-proxy'
    }
}

pipeline {
    agent none
    options {
        timeout(time: 1, unit: 'HOURS')
        ansiColor('xterm')
    }
    environment {
        BUNDLE_JOBS = 4
        BUNDLE_RETRY = 3
    }

    stages {
        stage('Rubocop') {
            agent any
            environment {
                BUNDLE_WITHOUT = 'bmc:development:dhcp_isc_inotify:dhcp_isc_kqueue:journald:krb5:libvirt:puppetca_token_whitelisting:realm_freeipa:windows'
                RUBY_VERSION = '2.7.6'
            }

            stages {
                stage('Setup Git Repos') {
                    steps {
                        script {
                            git_checkout()
                        }
                    }
                }
                stage('Install dependencies') {
                    steps {
                        bundleInstall(RUBY_VERSION)
                    }
                }
                stage('Run Rubocop') {
                    steps {
                        bundleExec(RUBY_VERSION, 'rubocop --format progress --out rubocop.log --format progress')
                    }
                    post {
                        always {
                            recordIssues tool: ruboCop(pattern: 'rubocop.log'), enabledForFailure: true
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
                    stage('Setup Git Repos') {
                        steps {
                            script {
                                git_checkout()
                            }
                        }
                    }
                    stage('Install dependencies') {
                        steps {
                            bundleInstall(ruby)
                        }
                    }
                    stage('Run Tests') {
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
                        archiveArtifacts artifacts: 'Gemfile.lock'
                        deleteDir()
                    }
                }
            }
        }
    }
}
