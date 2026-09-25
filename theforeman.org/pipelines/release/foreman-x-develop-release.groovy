def commit_hash = ''
def source_project_name = "${project_name}-${git_ref}-source-release"
def deb_packages_to_build = []
def deb_build_steps = [
    setup_sources: [:],
    executes: [:],
    rsync_packages: [:]
]

pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 3, unit: 'HOURS')
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '7'))
    }

    stages {
        stage('Build Package') {
            parallel {
                stage('Build Copr RPM') {
                    agent { label 'rpmbuild' }

                    when {
                        expression { build_rpm }
                    }
                    stages {
                        stage('Copy Source') {
                            steps {
                                script {
                                    artifact_path = "${pwd()}/artifacts"
                                    copyArtifacts(projectName: source_project_name, target: artifact_path)
                                    commit_hash = readFile("${artifact_path}/commit")
                                }
                            }
                        }
                        stage('Setup Environment') {
                            steps {
                                dir('foreman-packaging') {
                                    shallow_packaging_checkout('rpm/develop', true)
                                }
                                setup_obal()
                            }
                        }
                        stage('copr-release') {
                            steps {
                                dir('foreman-packaging') {
                                    withCredentials([file(credentialsId: 'theforeman-bot-copr', variable: 'copr_config')]) {
                                        obal(
                                            action: 'nightly',
                                            packages: rpm_source_package_name,
                                            extraVars: [
                                                'nightly_sourcefiles': artifact_path,
                                                'nightly_githash': commit_hash,
                                                'build_package_build_system': 'copr',
                                                'build_package_copr_config': copr_config
                                            ]
                                        )
                                    }
                                }
                            }
                        }
                    }
                    post {
                        cleanup {
                            deleteDir()
                        }
                    }
                }
                stage('Build DEB') {
                    agent { label 'debian' }
                    when {
                        expression { build_deb }
                    }
                    stages {
                        stage('Clone Packaging') {
                            steps {
                                dir('foreman-packaging') {
                                    shallow_packaging_checkout('deb/develop')
                                }
                            }
                        }
                        stage('Generate build config') {
                            steps {
                                script {
                                    for (os in foreman_debian_releases) {
                                        deb_packages_to_build.add([
                                            type: 'core',
                                            name: deb_source_package_name,
                                            path: "debian/${os}/${deb_source_package_name}",
                                            operating_system: os
                                        ])

                                        deb_build_steps = build_deb_package_steps(deb_packages_to_build, 'nightly', 'theforeman', true)
                                    }
                                }
                            }
                        }
                        stage('Setup sources') {
                            steps {
                                dir('foreman-packaging') {
                                    script {
                                        parallel deb_build_steps.setup_sources
                                    }
                                }
                            }
                        }
                        stage('Execute pbuilder') {
                            steps {
                                dir('foreman-packaging') {
                                    script {
                                        parallel deb_build_steps.executes
                                    }
                                }
                            }
                        }
                        stage('Stage packages') {
                            steps {
                                dir('foreman-packaging') {
                                    script {
                                        parallel deb_build_steps.rsync_packages
                                    }
                                }
                            }
                        }
                    }
                    post {
                        cleanup {
                            deleteDir()
                        }
                    }
                }
            }
        }
    }

    post {
        failure {
            notifyDiscourse(env, "${project_name} package release pipeline failed:", currentBuild.description)
        }
    }
}

def shallow_packaging_checkout(branch, fetch_annex = false) {
    def refspec = "+refs/heads/${branch}:refs/remotes/origin/${branch}"
    if (fetch_annex) {
        refspec += ' +refs/heads/synced/git-annex:refs/remotes/origin/synced/git-annex'
    }

    checkout changelog: false, poll: false, scm: [
        $class: 'GitSCM',
        branches: [[name: branch]],
        extensions: [[
            $class: 'CloneOption',
            depth: 1,
            honorRefspec: true,
            noTags: true,
            shallow: true
        ]],
        userRemoteConfigs: [[
            refspec: refspec,
            url: 'https://github.com/theforeman/foreman-packaging.git'
        ]]
    ]
}
