def katello_versions = [
    'master': [
        'foreman': 'develop',
        'ruby': ['2.7']
    ],
    'KATELLO-4.7': [
        'foreman': '3.5-stable',
        'ruby': ['2.7']
    ],
    'KATELLO-4.6': [
        'foreman': '3.4-stable',
        'ruby': ['2.7']
    ],
    'KATELLO-4.5': [
        'foreman': '3.3-stable',
        'ruby': ['2.7']
    ],
    'KATELLO-4.4': [
        'foreman': '3.2-stable',
        'ruby': ['2.7']
    ],
    'KATELLO-4.3': [
        'foreman': '3.1-stable',
        'ruby': ['2.7']
    ],
    'KATELLO-4.2': [
        'foreman': '3.0-stable',
        'ruby': ['2.7']
    ],
    //Testing of 3.18 to help with better testing of user issue fixes for migration
    'KATELLO-3.18': [
        'foreman': '2.3-stable',
        'ruby': ['2.5']
    ],
]

def ruby_versions = katello_versions[ghprbTargetBranch]['ruby']
def foreman_branch = katello_versions[ghprbTargetBranch]['foreman']

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
                        values '2.5', '2.7'
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
                            ghprb_git_checkout()

                            dir('foreman') {
                               git url: "https://github.com/theforeman/foreman", branch: foreman_branch, poll: false
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
                    stage('assets-precompile') {
                        steps {
                            dir('foreman') {
                                filter_package_json(ruby)

                                sh(script: "cp db/schema.rb.nulldb db/schema.rb", label: "copy nulldb schema")
                                withRVM(["bundle exec npm install --package-lock-only --no-audit"], ruby)
                                withRVM(["bundle exec npm ci --no-audit"], ruby)
                                withRVM(['bundle exec rake plugin:assets:precompile[katello] RAILS_ENV=production DATABASE_URL=nulldb://nohost --trace'], ruby)
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
                    stage('angular-ui') {
                        steps {
                            dir('engines/bastion') {
                                sh(script: "npm install --package-lock-only --no-audit", label: "npm install --package-lock-only")
                                sh(script: "npm ci --no-audit", label: "npm ci")
                                sh(script: "grunt ci", label: "grunt ci")
                            }
                            dir('engines/bastion_katello') {
                                sh(script: "npm install --package-lock-only --no-audit", label: "npm install --package-lock-only")
                                sh(script: "npm ci --no-audit", label: "npm ci")
                                sh(script: "grunt ci", label: "grunt ci")
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
