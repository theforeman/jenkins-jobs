// Maps a Konflux Component name to the variable prefix foremanctl's
// src/vars/images.yml uses for that component's image (confirmed against a real
// checkout of theforeman/foremanctl): each component has a
// `<prefix>_container_image` + `<prefix>_container_tag` pair which are joined as
// "{{ x_container_image }}:{{ x_container_tag }}" by the deploy roles. There is no
// CLI/extra-vars way to override these, so we sed-patch the checked-out file, same
// mechanism theforeman/foreman-oci-images' own CI workflow uses to pin a build tag.
def konflux_gate_image_var_prefix() {
    return [
        'candlepin-develop': 'candlepin',
        'foreman-develop': 'foreman',
        'foreman-proxy-develop': 'foreman_proxy',
        'pulp-develop': 'pulp',
    ]
}

// Last Snapshot for this Application that actually completed a Release
// (`status.conditions[type=Released,status=True]`), used as the fallback image
// source when a component's build times out this cycle. Relies on `jq` being
// present on the 'el' agent.
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

// Polls for a new Snapshot for `app`. `previous` must be the snapshot captured by
// katello.groovy *before* it triggered the rebuild (passed through as the
// PREVIOUS_SNAPSHOTS build parameter) — not recomputed here, since by the time this
// pipeline starts a fast build could already have landed, making "current latest"
// indistinguishable from "the one we're supposed to wait for" and stalling this loop
// until the timeout every single night. Returns [app, snapshot, stale] rather than
// failing the build on timeout, so one slow/broken component doesn't block gating
// the others (per-component timeout, not a whole-pipeline one).
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

// Release CRs carry a `release.appstudio.openshift.io/snapshot` label (confirmed on
// the live cluster), so lookups don't need a jsonpath scan of every Release.
def konflux_gate_find_existing_release(snapshot) {
    return sh(
        label: "check existing release: ${snapshot}",
        script: "${konflux_oc_bin()} get release -n ${konflux_namespace()} -l release.appstudio.openshift.io/snapshot=${snapshot} -o jsonpath='{.items[0].metadata.name}'",
        returnStdout: true
    ).trim()
}

// Returns the created Release's actual name (generateName means we don't know it
// up front). Uses `oc create`, not `apply` — generateName only makes sense as a
// create-time directive (there's no existing name to diff against), and
// find_existing_release() already guards against calling this when one exists.
def konflux_gate_create_release(snapshot, releasePlan) {
    // Matches the day/hour/minute-stamped naming Konflux itself uses for
    // auto-created Snapshots/Releases (e.g. candlepin-20260713-224811-000), so a
    // gate-created Release is identifiable at a glance and generateName's random
    // suffix only has to disambiguate retries within the same minute.
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
// oc create returns — Konflux's release-service can still fail after acceptance
// (signing, enterprise-contract policy, registry push errors), confirmed live on the
// cluster: completed Releases carry
// status.conditions[type=Released].{status,reason,message}. Returns null on timeout
// (treated as "unknown", not "failed") so one slow release pipeline doesn't crash the
// whole build.
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

// The published, digest-pinned image(s) for a settled Release
// (status.artifacts.images[].{name,shasum,urls}), confirmed live on the cluster. Only
// meaningful once konflux_gate_wait_for_release() reports succeeded: true.
def konflux_gate_release_artifacts(releaseName) {
    def json = sh(
        label: "release artifacts: ${releaseName}",
        script: "${konflux_oc_bin()} get release ${releaseName} -n ${konflux_namespace()} -o jsonpath='{.status.artifacts.images}'",
        returnStdout: true
    ).trim()
    return readJSON(text: json ?: '[]')
}

// Runs the real install/upgrade test against the staged images. Modeled directly on
// centos.org/pipelines/foreman-discovery-image-build.groovy's Duffy usage: a single
// root@duffy_box SSH session, no forklift/pipe-user layer. The Duffy pool used here
// (metal-ec2-c5n-centos-9s-x86_64) is bare metal, so nested libvirt/KVM works the
// same way it does for forklift's own Vagrant VMs today.
//
// imageRefs: Map of Konflux Component name -> digest-pinned containerImage ref
// (e.g. "candlepin-develop": "quay.io/foreman/stage/candlepin@sha256:...").
def konflux_gate_run_test(imageRefs) {
    def boxname = 'duffy_box'
    def var_prefix = konflux_gate_image_var_prefix()

    setupDuffyClient()
    provisionDuffy()

    try {
        stage('Prepare Duffy node') {
            // Mirrors foreman-oci-images' integration.yml setup steps (libvirt/vagrant,
            // Ansible, then foremanctl's own ./setup-environment) rather than inventing a
            // new bootstrap sequence.
            duffy_ssh('dnf install -y epel-release && dnf install -y libvirt vagrant qemu-kvm git python3 python3-pip make && systemctl enable --now libvirtd && vagrant plugin install vagrant-libvirt', boxname, './')
            duffy_ssh('pip3 install --upgrade ansible-core', boxname, './')
            duffy_ssh('git clone https://github.com/theforeman/foremanctl.git', boxname, './')
            // GITHUB_ACTIONS=true keeps setup-environment on the "install into the
            // system/host Python" path instead of creating a .venv it would activate for
            // only that one SSH session — each duffy_ssh call is a fresh shell, so a venv
            // activated here wouldn't be visible to the later forge/foremanctl calls.
            duffy_ssh('cd foremanctl && GITHUB_ACTIONS=true ./setup-environment', boxname, './')

            imageRefs.each { component, ref ->
                def prefix = var_prefix[component]
                if (!prefix) {
                    error("konflux_gate_run_test: no foremanctl images.yml var prefix known for component '${component}'")
                }

                // images.yml only exposes "<image>:<tag>" (no digest-aware CLI/extra-vars
                // override), so we split the digest ref and reassemble it across the two
                // fields: image="<repo>@sha256", tag="<hex>" -> "<repo>@sha256:<hex>".
                def parts = ref.tokenize('@')
                def repo = parts[0]
                def hex = parts[1].replace('sha256:', '')

                duffy_ssh("sed -i 's#^${prefix}_container_image:.*#${prefix}_container_image: ${repo}@sha256#' foremanctl/src/vars/images.yml", boxname, './')
                duffy_ssh("sed -i 's#^${prefix}_container_tag:.*#${prefix}_container_tag: \"${hex}\"#' foremanctl/src/vars/images.yml", boxname, './')
            }
        }

        try {
            stage('Deploy and test') {
                duffy_ssh('cd foremanctl && ./forge vms start --vms "quadlet"', boxname, './')
                duffy_ssh('cd foremanctl && ./forge setup-repositories', boxname, './')
                duffy_ssh('cd foremanctl && ./foremanctl deploy --initial-admin-password=changeme --tuning development --add-feature hammer --add-feature foreman-proxy --add-feature remote-execution', boxname, './')
                // Test scope confirmed by actually running `forge test` locally against
                // the real latest stage digests (not guessed):
                //  - tests/flavor/**: excluded, its own pulp_test.py collides with the
                //    top-level tests/pulp_test.py under pytest's default rootdir-relative
                //    import mode ("import file mismatch").
                //  - tests/feature/iop/**: excluded, requires --add-feature iop which we
                //    don't deploy here.
                //  - tests/backup_test.py, tests/certificate_bundle_test.py,
                //    tests/feature/foreman/base_test.py: excluded, they shell out to a
                //    relative './foremanctl' and error with FileNotFoundError regardless
                //    of image content — looks like a pytest-cwd issue in foremanctl
                //    itself, not something specific to this gate.
                //  - test_foreman_content_view: deselected, needs the separate "client"
                //    Vagrant VM, which we don't start (only "quadlet").
                // Everything else — including httpd/postgresql/valkey/foreman_target
                // health checks and the full candlepin/pulp test files — ran and passed
                // against the real Konflux stage images.
                duffy_ssh('cd foremanctl && ./forge test --pytest-args="tests --ignore=tests/flavor --ignore=tests/feature/iop --ignore=tests/backup_test.py --ignore=tests/certificate_bundle_test.py --ignore=tests/feature/foreman/base_test.py --deselect tests/feature/katello/client_test.py::test_foreman_content_view"', boxname, './')
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
