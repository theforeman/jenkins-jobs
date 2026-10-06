// Populated by resolve-snapshots / assemble-image-refs, consumed by gate-test and
// release. Declared at script scope (outside pipeline{}) so plain assignment from a
// stage's script{} block is visible to later stages, the same way konflux_components
// etc. from the vars file are visible here.
def GATE_RESULTS = [:]
def GATE_IMAGE_REFS = [:]
def GATE_HAS_UPDATES = false

pipeline {
    agent { label 'el' }

    parameters {
        // Nightly receives the metadata captured immediately before its rebuild.
        // Stable streams select existing staged images and leave these empty.
        string(name: 'PREVIOUS_SNAPSHOTS', defaultValue: '{}', description: 'Nightly only: JSON map of Konflux Application name -> Snapshot name, captured immediately before the upstream Katello pipeline triggered the Konflux rebuild.')
        string(name: 'REBUILD_STARTED_AT', defaultValue: '', description: 'Nightly only: Unix timestamp in seconds captured immediately before triggering the Konflux component rebuilds.')
    }

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('resolve-snapshots') {
            steps {
                script {
                    def previousSnapshots = readJSON(text: params.PREVIOUS_SNAPSHOTS ?: '{}')
                    def rebuildStartedAt = params.REBUILD_STARTED_AT?.trim()
                    if (konflux_gate_rebuild && (!rebuildStartedAt || !rebuildStartedAt.isLong() || rebuildStartedAt.toLong() <= 0)) {
                        error('REBUILD_STARTED_AT must contain the pre-rebuild Unix timestamp in seconds')
                    }

                    try {
                        konflux_login()

                        def results = [:]
                        konflux_gate_applications.each { app, components ->
                            if (konflux_gate_rebuild) {
                                def previous = previousSnapshots[app]
                                if (!previous) {
                                    echo "No captured previous snapshot for '${app}'; requiring all component builds to start at or after ${rebuildStartedAt}"
                                }
                                results[app] = konflux_gate_wait_for_new_snapshot(app, components, previous, rebuildStartedAt, snapshot_wait_timeout_minutes)
                            } else {
                                results[app] = konflux_gate_existing_snapshot(app, components, konflux_gate_release_plans[app])
                            }
                            echo "${app}: snapshot=${results[app].snapshot} stale=${results[app].stale}"
                        }
                        GATE_RESULTS = results
                        GATE_HAS_UPDATES = results.values().any { !it.stale }
                        if (!konflux_gate_rebuild && !GATE_HAS_UPDATES) {
                            currentBuild.description = 'No complete unpublished stable Snapshots; deployment and release skipped.'
                            echo currentBuild.description
                        }
                    } finally {
                        konflux_logout()
                    }
                }
            }
        }
        stage('assemble-image-refs') {
            when { expression { konflux_gate_rebuild || GATE_HAS_UPDATES } }
            steps {
                script {
                    try {
                        konflux_login()

                        def refs = [:]
                        GATE_RESULTS.each { app, result ->
                            if (!result.snapshot) {
                                error("Cannot test the image set without a complete Snapshot for '${app}'")
                            }
                            konflux_gate_applications[app].each { component ->
                                refs[component] = konflux_gate_image_ref(result.snapshot, component)
                                echo "${component}: ${refs[component]}"
                            }
                        }
                        GATE_IMAGE_REFS = refs
                    } finally {
                        konflux_logout()
                    }
                }
            }
        }
        stage('gate-test') {
            when { expression { konflux_gate_rebuild || GATE_HAS_UPDATES } }
            steps {
                script {
                    konflux_gate_run_test(GATE_IMAGE_REFS, konflux_gate_foremanctl_branch, konflux_gate_pytest_args)
                }
            }
        }
        stage('release') {
            when { expression { konflux_gate_rebuild || GATE_HAS_UPDATES } }
            steps {
                script {
                    def published = []

                    try {
                        konflux_login()

                        GATE_RESULTS.each { app, result ->
                            if (result.stale) {
                                echo "Skipping release for '${app}': snapshot ${result.snapshot} is a fallback or is already published"
                                return
                            }

                            def releaseName = konflux_gate_find_existing_release(result.snapshot, konflux_gate_release_plans[app])
                            if (releaseName) {
                                echo "Release already exists for snapshot ${result.snapshot}: ${releaseName}"
                            } else {
                                releaseName = konflux_gate_create_release(result.snapshot, konflux_gate_release_plans[app])
                            }

                            def outcome = konflux_gate_wait_for_release(releaseName, release_wait_timeout_minutes)
                            if (outcome == null) {
                                echo "WARNING: release '${releaseName}' for '${app}' did not settle within ${release_wait_timeout_minutes}m"
                                published << "${app}: ${releaseName} — TIMED OUT waiting for release to settle"
                                currentBuild.result = 'UNSTABLE'
                            } else if (!outcome.succeeded) {
                                echo "Release '${releaseName}' for '${app}' failed: ${outcome.reason} — ${outcome.message}"
                                published << "${app}: ${releaseName} — FAILED (${outcome.reason}: ${outcome.message})"
                                currentBuild.result = 'UNSTABLE'
                            } else {
                                konflux_gate_release_artifacts(releaseName).each { image ->
                                    published << "${image.name}: ${image.urls[0]}@${image.shasum}"
                                }
                            }
                        }
                    } finally {
                        konflux_logout()
                    }

                    if (published) {
                        currentBuild.description = ([currentBuild.description, 'Published:'] + published).minus(null).join('\n')
                    }
                }
            }
        }
    }
    post {
        failure {
            notifyDiscourse(env, 'Konflux gate pipeline failed:', currentBuild.description)
        }
        unstable {
            notifyDiscourse(env, 'Konflux gate pipeline is unstable:', currentBuild.description)
        }
    }
}
