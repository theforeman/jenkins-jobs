pipeline {
    agent { label 'el' }

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('staging-build-repository') {
            when {
                expression { katello_version == 'nightly' }
            }
            steps {
                git url: "https://github.com/theforeman/theforeman-rel-eng", poll: false

                script {
                    foreman_el_releases.each { distro ->
                        sh "./build_stage_repository katello ${katello_version} ${distro} ${foreman_version}"
                    }
                }
            }
        }
        stage('staging-copy-repository') {
            when {
                expression { katello_version == 'nightly' }
            }
            steps {
                script {
                    rsync_to_yum_stage('katello', katello_version)
                }
            }
        }
        stage('staging-repoclosure') {
            steps {
                script {
                    parallel repoclosures('katello-staging', foreman_el_releases, foreman_version)
                }
            }
            post {
                always {
                    deleteDir()
                }
            }
        }
        stage('staging-test') {
            agent any

            steps {
                script {
                    runDuffyPipeline('katello-rpm', katello_version)
                }
            }
        }
        stage('staging-push-rpms') {
            agent { label 'sshkey' }

            steps {
                script {
                    foreman_el_releases.each { distro ->
                        push_foreman_staging_rpms('katello', katello_version, distro)
                    }
                }
            }
        }
        stage('capture-konflux-snapshots') {
            // Captured here, before the rebuild is triggered, and passed to the
            // konflux_gate_job_name job as a build parameter — not recomputed by that
            // job once it starts. Konflux build+snapshot latency isn't bounded tightly
            // enough to trust "whatever's latest when the gate job happens to start" as
            // "the pre-rebuild snapshot": a fast build could already have landed by
            // then, making previous == latest and stalling the gate's wait loop forever
            // waiting for something newer that will never come.
            when {
                expression {
                    try {
                        konflux_components as boolean
                    } catch (MissingPropertyException ignored) {
                        false
                    }
                }
            }

            steps {
                script {
                    try {
                        konflux_login()

                        def previous = [:]
                        konflux_gate_applications.each { app, components ->
                            previous[app] = konflux_latest_snapshot(app)
                        }
                        env.KONFLUX_PREVIOUS_SNAPSHOTS = writeJSON(returnText: true, json: previous)
                    } finally {
                        konflux_logout()
                    }
                }
            }
        }
        stage('trigger-konflux-rebuild') {
            when {
                expression {
                    try {
                        konflux_components as boolean
                    } catch (MissingPropertyException ignored) {
                        false
                    }
                }
            }

            steps {
                script {
                    retrigger_konflux_components(konflux_components)
                    build(
                        job: konflux_gate_job_name,
                        wait: false,
                        parameters: [string(name: 'PREVIOUS_SNAPSHOTS', value: env.KONFLUX_PREVIOUS_SNAPSHOTS)]
                    )
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, "Katello ${katello_version} pipeline failed:", currentBuild.description)
        }
    }
}
