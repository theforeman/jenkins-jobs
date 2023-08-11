def packages_to_build

pipeline {
    agent { label 'rpmbuild' }

    options {
        timestamps()
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('Clone Packaging') {
            steps {
                script {
                    foreman_branch = foreman_version == 'nightly' ? "rpm/develop" : "rpm/${foreman_version}"
                }

                checkout([
                    $class : 'GitSCM',
                    branches : [[name: "*/${foreman_branch}"]],
                    extensions: [[$class: 'CleanCheckout']],
                    userRemoteConfigs: [
                        [url: 'https://github.com/theforeman/foreman-packaging']
                    ]
                ])

            }
        }
        stage('Find packages') {
            steps {
                copyArtifacts(projectName: env.JOB_NAME, optional: true)

                script {

                    if (fileExists('commit')) {

                        commit = readFile(file: 'commit').trim()
                        packages_to_build = find_changed_packages("${commit}..HEAD")

                    } else {

                        packages_to_build = []

                    }
                }
            }
        }
        stage('Release Build Packages') {
            when {
                expression { packages_to_build != [] }
            }
            steps {

                setup_obal()

                withCredentials([file(credentialsId: 'theforeman-bot-copr', variable: 'copr_config')]) {
                    obal(
                        action: "release",
                        extraVars: [
                            'build_package_build_system': 'copr',
                            'build_package_copr_config': copr_config,
                        ],
                        packages: packages_to_build
                    )
                }

            }
        }
    }

    post {
        success {
            archive_git_hash()
        }
        failure {
            notifyDiscourse(
              env,
              "${env.JOB_NAME} failed for ${packages_to_build.join(',')}",
              "Foreman RPM packaging release job failed: ${env.BUILD_URL}"
            )
        }
        cleanup {
            deleteDir()
        }
    }
}
