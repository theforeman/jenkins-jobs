pipeline {
    agent none
    options {
        timeout(time: 1, unit: 'HOURS')
        ansiColor('xterm')
    }

    stages {
        stage('Test') {
            matrix {
                agent any
                axes {
                    axis {
                        name 'ruby'
                        values '2.7.6'
                    }
                    axis {
                        name 'PUPPET_VERSION'
                        values '7.0'
                    }
                }
                excludes {
                    exclude {
                        axis {
                            name 'ruby'
                            notValues '2.7.6'
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
                    stage("bundle-install") {
                        steps {
                            bundleInstall(ruby, "--gemfile Gemfile.${ruby}-${PUPPET_VERSION}")
                        }
                    }
                    stage('Run Rubocop') {
                        steps {
                            bundleExec(ruby, "rake rubocop TESTOPTS='-v' --trace", "Gemfile.${ruby}-${PUPPET_VERSION}")
                        }
                    }
                    stage('Run Tests') {
                        steps {
                            bundleExec(ruby, "rake spec TESTOPTS='-v' --trace", "Gemfile.${ruby}-${PUPPET_VERSION}")
                        }
                    }
                    stage('Test installer configuration') {
                        steps {
                            bundleExec(ruby, "rake install PREFIX=${ruby}-${PUPPET_VERSION} --trace", "Gemfile.${ruby}-${PUPPET_VERSION}")
                            bundleExec(ruby, "${ruby}-${PUPPET_VERSION}/sbin/foreman-installer --help --scenario foreman --trace", "Gemfile.${ruby}-${PUPPET_VERSION}")
                            bundleExec(ruby, "${ruby}-${PUPPET_VERSION}/sbin/foreman-installer --help --scenario katello --trace", "Gemfile.${ruby}-${PUPPET_VERSION}")
                            bundleExec(ruby, "${ruby}-${PUPPET_VERSION}/sbin/foreman-installer --help --scenario foreman-proxy-content --trace", "Gemfile.${ruby}-${PUPPET_VERSION}")
                            bundleExec(ruby, "${ruby}-${PUPPET_VERSION}/sbin/foreman-proxy-certs-generate --help --trace", "Gemfile.${ruby}-${PUPPET_VERSION}")
                            bundleExec(ruby, "${ruby}-${PUPPET_VERSION}/sbin/foreman-proxy-certs-generate --help|grep -q certs-update-server", "Gemfile.${ruby}-${PUPPET_VERSION}")
                        }
                    }
                }
                post {
                    always {
                        archiveArtifacts artifacts: "Gemfile*lock"
                        deleteDir()
                    }
                }
            }
        }
    }
}
