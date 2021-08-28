def packages_to_build
def build_steps = [
    setup_sources: [:],
    executes: [:],
    rsync_packages: [:]
]

pipeline {
    agent { label 'debian' }

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
                    foreman_branch = foreman_version == 'nightly' ? "deb/develop" : "deb/${foreman_version}"
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
                        packages_to_build = find_changed_debs("${commit}..HEAD")
                        build_steps = build_deb_package_steps(packages_to_build, foreman_version, 'theforeman', false)

                    }
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

    post {
        success {
            archive_git_hash()
        }
        failure {
            notifyDiscourse(
              env,
              "${env.JOB_NAME} failed for ${packages_to_build.join(',')}",
              "Foreman Debian packaging release job failed: ${env.BUILD_URL}"
            )
        }
        cleanup {
            deleteDir()
        }
    }
}
