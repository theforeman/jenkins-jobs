// Maps a Konflux Component name to the variable prefix in foremanctl's
// src/vars/images.yml (<prefix>_container_image/_container_tag). No CLI/extra-vars
// override exists, so we sed-patch the file instead.
def konflux_gate_image_var_prefix() {
    return [
        'candlepin-develop': 'candlepin',
        'foreman-develop': 'foreman',
        'foreman-proxy-develop': 'foreman_proxy',
        'pulp-develop': 'pulp',
    ]
}

// Fallback image source when a component's build times out this cycle: the last
// Snapshot that actually completed a Release. Relies on `jq` on the 'el' agent.
def konflux_gate_last_released_snapshot(app) {
    return sh(
        label: "resolve last released snapshot: ${app}",
        script: """
            ${konflux_oc_bin()} get release -n ${konflux_namespace()} -l appstudio.openshift.io/application=${app} -o json \
              | jq -r '[.items[] | select(.status.conditions[]? | .type=="Released" and .status=="True")] | sort_by(.metadata.creationTimestamp) | last | .spec.snapshot // empty'
        """,
        returnStdout: true
    ).trim()
}

// Polls for a new Snapshot for `app`. `previous` is captured by katello.groovy
// *before* it triggers the rebuild (see PREVIOUS_SNAPSHOTS) — recomputing it here
// would race with Konflux's own build latency. Per-component timeout, not
// whole-pipeline, so one broken component doesn't block gating the others.
def konflux_gate_wait_for_new_snapshot(app, previous, timeoutMinutes) {
    def resolved = previous
    try {
        timeout(time: timeoutMinutes, unit: 'MINUTES') {
            waitUntil {
                resolved = konflux_latest_snapshot(app)
                return resolved && resolved != previous
            }
        }
        return [app: app, snapshot: resolved, stale: false]
    } catch (Exception ex) {
        echo "Timed out waiting for a new Konflux Snapshot for '${app}' after ${timeoutMinutes}m; falling back to the last released Snapshot"
        def fallback = konflux_gate_last_released_snapshot(app)
        return [app: app, snapshot: fallback, stale: true]
    }
}

def konflux_gate_image_ref(snapshot, component) {
    return sh(
        label: "resolve image ref: ${component}@${snapshot}",
        script: "${konflux_oc_bin()} get snapshot ${snapshot} -n ${konflux_namespace()} -o jsonpath=\"{.spec.components[?(@.name=='${component}')].containerImage}\"",
        returnStdout: true
    ).trim()
}

def konflux_gate_find_existing_release(snapshot) {
    return sh(
        label: "check existing release: ${snapshot}",
        script: "${konflux_oc_bin()} get release -n ${konflux_namespace()} -l release.appstudio.openshift.io/snapshot=${snapshot} -o jsonpath='{.items[0].metadata.name}'",
        returnStdout: true
    ).trim()
}

// Returns the created Release's actual name (generateName means we don't know it up
// front). Uses `oc create`, not `apply` — there's no existing name to diff against.
def konflux_gate_create_release(snapshot, releasePlan) {
    def timestamp = sh(script: 'date -u +%Y%m%d-%H%M', label: 'timestamp release name', returnStdout: true).trim()
    def release_yaml = """apiVersion: appstudio.redhat.com/v1alpha1
kind: Release
metadata:
  generateName: ${snapshot}-gate-${timestamp}-
  namespace: ${konflux_namespace()}
spec:
  snapshot: ${snapshot}
  releasePlan: ${releasePlan}
"""

    writeFile(file: 'release.yaml', text: release_yaml)
    try {
        return sh(
            label: "create release: ${snapshot}",
            script: "${konflux_oc_bin()} create -f release.yaml -o jsonpath='{.metadata.name}'",
            returnStdout: true
        ).trim()
    } finally {
        sh(label: 'remove release manifest', script: 'rm -f release.yaml')
    }
}

// Polls the Release CR's own `Released` condition rather than assuming success once
// oc create returns — release-service can still fail after acceptance (signing, EC
// policy, registry push). Returns null on timeout ("unknown", not "failed").
def konflux_gate_wait_for_release(releaseName, timeoutMinutes) {
    def status = ''
    try {
        timeout(time: timeoutMinutes, unit: 'MINUTES') {
            waitUntil {
                status = sh(
                    label: "poll release status: ${releaseName}",
                    script: "${konflux_oc_bin()} get release ${releaseName} -n ${konflux_namespace()} -o jsonpath='{.status.conditions[?(@.type==\"Released\")].status}'",
                    returnStdout: true
                ).trim()
                return status == 'True' || status == 'False'
            }
        }
    } catch (Exception ex) {
        echo "Timed out after ${timeoutMinutes}m waiting for Release '${releaseName}' to settle"
        return null
    }

    def reason = sh(
        label: "release reason: ${releaseName}",
        script: "${konflux_oc_bin()} get release ${releaseName} -n ${konflux_namespace()} -o jsonpath='{.status.conditions[?(@.type==\"Released\")].reason}'",
        returnStdout: true
    ).trim()
    def message = sh(
        label: "release message: ${releaseName}",
        script: "${konflux_oc_bin()} get release ${releaseName} -n ${konflux_namespace()} -o jsonpath='{.status.conditions[?(@.type==\"Released\")].message}'",
        returnStdout: true
    ).trim()

    return [succeeded: status == 'True', reason: reason, message: message]
}

// The published, digest-pinned image(s) for a settled Release. Only meaningful once
// konflux_gate_wait_for_release() reports succeeded: true.
def konflux_gate_release_artifacts(releaseName) {
    def json = sh(
        label: "release artifacts: ${releaseName}",
        script: "${konflux_oc_bin()} get release ${releaseName} -n ${konflux_namespace()} -o jsonpath='{.status.artifacts.images}'",
        returnStdout: true
    ).trim()
    return readJSON(text: json ?: '[]')
}

// Runs the real install/upgrade test against the staged images, directly on a Duffy
// box on ci.theforeman.org (no ci.centos.org delegation, no forklift).
//
// imageRefs: Map of Konflux Component name -> digest-pinned containerImage ref
// (e.g. "candlepin-develop": "quay.io/foreman/stage/candlepin@sha256:...").
def konflux_gate_run_test(imageRefs) {
    def boxname = 'duffy_box'
    def var_prefix = konflux_gate_image_var_prefix()

    // No ambient CICO_API_KEY here (this runs on ci.theforeman.org, not ci.centos.org),
    // so it's bound explicitly, same as konflux-jenkins-trigger-token.
    withCredentials([string(credentialsId: 'cico-api-key', variable: 'CICO_API_KEY')]) {
        setupDuffyClient()
    }
    provisionDuffy()

    try {
        stage('Prepare Duffy node') {
            def duffy_session = readFile(file: 'jenkins-jobs/centos.org/ansible/duffy_session')
            runPlaybook(
                playbook: 'jenkins-jobs/theforeman.org/ansible/setup_vagrant_libvirt.yml',
                inventory: duffy_inventory('./'),
                limit: "duffy_session_${duffy_session}",
                options: ['-b'],
            )

            duffy_ssh('git clone https://github.com/theforeman/foremanctl.git', boxname, './')
            // GITHUB_ACTIONS=true skips the venv setup-environment would otherwise
            // create — each duffy_ssh call is a fresh shell, so it wouldn't be active
            // for the later forge/foremanctl calls anyway.
            duffy_ssh('cd foremanctl && GITHUB_ACTIONS=true ./setup-environment', boxname, './')

            imageRefs.each { component, ref ->
                def prefix = var_prefix[component]
                if (!prefix) {
                    error("konflux_gate_run_test: no foremanctl images.yml var prefix known for component '${component}'")
                }

                // "<repo>@sha256" / "<hex>" -> "<repo>@sha256:<hex>"
                def parts = ref.tokenize('@')
                def repo = parts[0]
                def hex = parts[1].replace('sha256:', '')

                duffy_ssh("sed -i 's#^${prefix}_container_image:.*#${prefix}_container_image: ${repo}@sha256#' foremanctl/src/vars/images.yml", boxname, './')
                duffy_ssh("sed -i 's#^${prefix}_container_tag:.*#${prefix}_container_tag: \"${hex}\"#' foremanctl/src/vars/images.yml", boxname, './')
            }
        }

        try {
            stage('Deploy and test') {
                // No --vms flag: defaults to "quadlet client" (forge's own default),
                // needed for test_foreman_content_view.
                duffy_ssh('cd foremanctl && ./forge vms start', boxname, './')
                duffy_ssh('cd foremanctl && ./forge setup-repositories', boxname, './')
                duffy_ssh('cd foremanctl && ./foremanctl deploy --initial-admin-password=changeme --tuning development --add-feature hammer --add-feature foreman-proxy --add-feature remote-execution', boxname, './')
                // Exclusions confirmed against real stage images, 0 failed/errored
                // otherwise (238 passed): tests/flavor collides with top-level
                // pulp_test.py; iop needs --add-feature iop; the initial_organization/
                // location tests expect a "Foreman CI"/"Internet" naming convention
                // (foreman_initial_organization defaults to "Default Organization")
                // that we don't opt into.
                duffy_ssh('cd foremanctl && ./forge test --pytest-args="--ignore=tests/flavor --ignore=tests/feature/iop -k \'not test_foreman_initial_organization and not test_foreman_initial_location\'"', boxname, './')
            }
        } catch (Exception ex) {
            stage('Collect sos reports') {
                duffy_ssh('cd foremanctl && ./forge sos', boxname, './')
                duffy_scp('foremanctl/sos', "${env.WORKSPACE}/sos", boxname, './')
                archiveArtifacts artifacts: 'sos/**', allowEmptyArchive: true
            }
            throw ex
        }
    } finally {
        deprovisionDuffy()
    }
}
