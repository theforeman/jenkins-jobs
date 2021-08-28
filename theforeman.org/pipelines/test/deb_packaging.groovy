def packages_to_build
def build_steps

pipeline {
    agent { label 'debian' }

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '7'))
    }

    stages {
        stage('Clone Packaging') {
            steps {

                deleteDir()
                ghprb_git_checkout()

            }
        }

        stage('Find Packages to Build') {
            steps {

                script {
                    def version = ghprbTargetBranch == 'deb/develop' ? 'nightly' : ghprbTargetBranch.replace('deb/', '')
                    packages_to_build = find_changed_debs("origin/${ghprbTargetBranch}")
                    build_steps = build_deb_package_steps(packages_to_build, version, ghprbPullAuthorLogin, true)
                }

            }
        }

        stage('Setup sources') {
            steps {
                script {
                    parallel build_steps.setup_sources
                }
            }
        }

        stage('Execute pbuilder') {
            steps {
                script {
                    parallel build_steps.executes
                }
            }
        }

        stage('Stage packages') {
            steps {
                script {
                    parallel build_steps.rsync_packages
                }
            }
        }
    }
}
