// Populated by wait-for-snapshots / assemble-image-refs, consumed by gate-test and
// release. Declared at script scope (outside pipeline{}) so plain assignment from a
// stage's script{} block is visible to later stages, the same way konflux_components
// etc. from the vars file are visible here.
def GATE_RESULTS = [:]
def GATE_IMAGE_REFS = [:]

pipeline {
    agent { label 'el' }

    parameters {
        // Populated by katello.groovy's capture-konflux-snapshots stage and passed via
        // `build job: konflux_gate_job_name, parameters: [...]` — see
        // wait-for-snapshots below for why this can't just be recomputed here.
        string(name: 'PREVIOUS_SNAPSHOTS', defaultValue: '{}', description: 'JSON map of Konflux Application name -> Snapshot name, captured immediately before the upstream katello RPM pipeline triggered the Konflux rebuild for this stream.')
    }

    options {
        timestamps()
        timeout(time: 5, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('wait-for-snapshots') {
            steps {
                script {
                    // PREVIOUS_SNAPSHOTS is captured by katello.groovy's
                    // capture-konflux-snapshots stage right before it triggers the
                    // rebuild, and passed in as a build parameter — deliberately not
                    // recomputed here. This job's start time relative to Konflux's
                    // build+snapshot latency isn't bounded, so "whatever's latest when
                    // we happen to start" can't be trusted as "the snapshot from before
                    // this cycle's rebuild".
                    def previousSnapshots = readJSON(text: params.PREVIOUS_SNAPSHOTS ?: '{}')

                    try {
                        konflux_login()

                        def results = [:]
                        konflux_gate_applications.each { app, components ->
                            def previous = previousSnapshots[app]
                            if (!previous) {
                                echo "WARNING: no captured previous snapshot for '${app}' in PREVIOUS_SNAPSHOTS; treating whatever is currently latest as new"
                            }
                            results[app] = konflux_gate_wait_for_new_snapshot(app, previous, snapshot_wait_timeout_minutes)
                            echo "${app}: snapshot=${results[app].snapshot} stale=${results[app].stale}"
                        }
                        GATE_RESULTS = results
                    } finally {
                        konflux_logout()
                    }
                }
            }
        }
        stage('assemble-image-refs') {
            steps {
                script {
                    try {
                        konflux_login()

                        def refs = [:]
                        GATE_RESULTS.each { app, result ->
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
            steps {
                script {
                    konflux_gate_run_test(GATE_IMAGE_REFS)
                }
            }
        }
        stage('release') {
            steps {
                script {
                    def published = []

                    try {
                        konflux_login()

                        GATE_RESULTS.each { app, result ->
                            if (result.stale) {
                                echo "Skipping release for '${app}': snapshot ${result.snapshot} is a stale fallback, not new this cycle"
                                return
                            }

                            def releaseName = konflux_gate_find_existing_release(result.snapshot)
                            if (releaseName) {
                                echo "Release already exists for snapshot ${result.snapshot}: ${releaseName}"
                            } else {
                                releaseName = konflux_gate_create_release(result.snapshot, konflux_gate_release_plans[app])
                            }

                            // Don't just assume oc create succeeding means the image landed
                            // in production — Konflux's release-service can still fail
                            // after acceptance (signing, EC policy, registry push), so poll
                            // the Release's own status before calling it done.
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
    }
}
