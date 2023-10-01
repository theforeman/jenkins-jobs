def packages_to_build
def packages = [:]
def VERCMP_NEWER = 12
def VERCMP_OLDER = 11
def VERCMP_EQUAL = 0

pipeline {
    agent { label 'rpmbuild' }

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
                setup_obal()

            }
        }

        stage('Find Packages to Build') {
            steps {

                script {
                    packages_to_build = find_changed_packages("origin/${ghprbTargetBranch}")

                    update_build_description_from_packages(packages_to_build)
                }

            }
        }

        stage('Lint Spec') {
            when {
                expression { packages_to_build }
            }
            steps {

                script {
                    for(int i = 0; i < packages_to_build.size(); i++) {
                        def index = i
                        packages[packages_to_build[index]] = {
                            obal(action: "lint", packages: packages_to_build[index])
                        }
                    }

                    parallel packages
                }
            }
        }

        stage("Verify version and release"){
            when {
                expression { packages_to_build }
                expression { ghprbTargetBranch == 'rpm/develop'}
            }
            steps {
                script {

                    for(int i = 0; i < packages_to_build.size(); i++) {
                        package_name = packages_to_build[i]
                        spec_pattern = "packages/**/${package_name}.spec"

                        old_spec_path = find_deleted_files("origin/${env.ghprbTargetBranch}", spec_pattern)
                        if (! old_spec_path) {
                            old_spec_path = find_changed_files("origin/${env.ghprbTargetBranch}", spec_pattern)
                        }


                        if (old_spec_path) {
                            new_spec_path = find_added_or_changed_files("origin/${env.ghprbTargetBranch}", spec_pattern)
                            if (old_spec_path != new_spec_path) {
                              continue
                            }

                            sh(script: "git checkout origin/${env.ghprbTargetBranch}", label: "git checkout target_branch")
                            old_version = query_rpmspec(old_spec_path, '%{VERSION}')
                            old_release = query_rpmspec(old_spec_path, '%{RELEASE}')

                            sh(script: "git checkout -", label: "git checkout source_branch")
                            new_version = query_rpmspec(new_spec_path, '%{VERSION}')
                            new_release = query_rpmspec(new_spec_path, '%{RELEASE}')

                            compare_version = sh(
                              script: "rpmdev-vercmp ${old_version} ${new_version}",
                              returnStatus: true,
                              label: "rpmdev-vercmp"
                            )

                            compare_release = sh(
                              script: "rpmdev-vercmp ${old_release} ${new_release}",
                              returnStatus: true,
                              label: "rpmdev-vercmp"
                            )

                            compare_new_to_one = sh(
                              script: "rpmdev-vercmp 1 ${new_release}",
                              returnStatus: true,
                              label: "rpmdev-vercmp"
                            )

                            if (compare_version != VERCMP_EQUAL && (compare_new_to_one == VERCMP_OLDER || compare_new_to_one == VERCMP_EQUAL)) {
                                echo "New version and release is reset to 1 for ${package_name}"
                            } else if (compare_version != VERCMP_EQUAL && compare_new_to_one == VERCMP_NEWER) {
                                // new version, but release was not reset
                                sh """
                                    echo 'Version updated but release was not reset back to 1 for ${package_name}"'
                                    exit 1
                                """
                            } else if (compare_version == VERCMP_EQUAL && compare_release == VERCMP_NEWER) {
                                echo "Version remained the same and release is reset to 1 for ${package_name}"
                            } else {
                                sh """
                                    echo 'Version or release needs updating for ${package_name}"'
                                    exit 1
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Scratch Build Packages') {
            when {
                expression { packages_to_build }
            }
            steps {

                withCredentials([file(credentialsId: 'theforeman-bot-copr', variable: 'copr_config')]) {
                    obal(
                        action: "scratch",
                        extraVars: [
                            'build_package_build_system': 'copr',
                            'build_package_archive_build_info': 'True',
                            'build_package_copr_config': copr_config,
                        ],
                        packages: packages_to_build
                    )
                }

            }
        }

        stage('Repoclosure') {
            when {
                expression { packages_to_build }
            }
            steps {

                script {
                    for(String package_name: packages_to_build) {
                        repos = copr_repos(package_name)

                        for(Map repo: repos) {
                            obal(
                                action: "repoclosure",
                                packages: package_name,
                                extraVars: [
                                    'repoclosure_check_repos': [repo['url']],
                                    'repoclosure_target_dist': repo['dist']
                                ]
                            )
                        }
                    }
                }

            }
        }
    }

    post {
        always {
            status_copr_links(ghprbGhRepository.split('/')[1])
        }
    }
}
