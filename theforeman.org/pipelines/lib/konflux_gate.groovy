def konflux_gate_image_var_prefix() {
    return [
        'candlepin-develop': 'candlepin',
        'foreman-develop': 'foreman',
        'foreman-proxy-develop': 'foreman_proxy',
        'pulp-develop': 'pulp',
        'candlepin-5-0': 'candlepin',
        'foreman-5-0': 'foreman',
        'foreman-proxy-5-0': 'foreman_proxy',
        'pulp-5-0': 'pulp',
    ]
}

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

def konflux_gate_wait_for_new_snapshot(app, previous, timeoutMinutes) {
    def resolved = previous
    try {
        timeout(time: timeoutMinutes, unit: 'MINUTES') {
            waitUntil {
                try {
                    resolved = konflux_latest_snapshot(app)
                    return resolved && resolved != previous
                } catch (Exception ex) {
                    echo "Unable to resolve the latest Konflux Snapshot for '${app}'; retrying: ${ex.message}"
                    return false
                }
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

def konflux_gate_release_artifacts(releaseName) {
    def json = sh(
        label: "release artifacts: ${releaseName}",
        script: "${konflux_oc_bin()} get release ${releaseName} -n ${konflux_namespace()} -o jsonpath='{.status.artifacts.images}'",
        returnStdout: true
    ).trim()
    return readJSON(text: json ?: '[]')
}

def konflux_gate_run_test(imageRefs) {
    def boxname = 'duffy_box'
    def var_prefix = konflux_gate_image_var_prefix()
    def duffy_home = pwd(tmp: true)

    try {
        withEnv(["HOME=${duffy_home}"]) {
            withCredentials([string(credentialsId: 'theforeman-duffy', variable: 'CICO_API_KEY')]) {
                setupDuffyClient()
            }
            provisionDuffy()
        }

        stage('Prepare Duffy node') {
            withEnv(["HOME=${duffy_home}"]) {
                def duffy_session = readFile(file: 'jenkins-jobs/centos.org/ansible/duffy_session')
                runPlaybook(
                    playbook: 'jenkins-jobs/theforeman.org/ansible/setup_vagrant_libvirt.yml',
                    inventory: duffy_inventory('./'),
                    limit: "duffy_session_${duffy_session}",
                    options: ['-b'],
                )
            }

            duffy_ssh('git clone https://github.com/theforeman/foremanctl.git', boxname, './')
            duffy_ssh('cd foremanctl && GITHUB_ACTIONS=true ./setup-environment', boxname, './')

            imageRefs.each { component, ref ->
                def prefix = var_prefix[component]
                if (!prefix) {
                    error("konflux_gate_run_test: no foremanctl images.yml var prefix known for component '${component}'")
                }

                def parts = ref.tokenize('@')
                def repo = parts[0]
                def hex = parts[1].replace('sha256:', '')

                duffy_ssh("sed -i 's#^${prefix}_container_image:.*#${prefix}_container_image: ${repo}@sha256#' foremanctl/src/vars/images.yml", boxname, './')
                duffy_ssh("sed -i 's#^${prefix}_container_tag:.*#${prefix}_container_tag: \"${hex}\"#' foremanctl/src/vars/images.yml", boxname, './')
            }
        }

        try {
            stage('Deploy and test') {
                duffy_ssh('cd foremanctl && ./forge vms start', boxname, './')
                duffy_ssh('cd foremanctl && ./forge setup-repositories', boxname, './')
                duffy_ssh('cd foremanctl && ./foremanctl deploy --initial-admin-password=changeme --tuning development --add-feature hammer --add-feature foreman-proxy --add-feature remote-execution', boxname, './')
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
        try {
            withEnv(["HOME=${duffy_home}"]) {
                deprovisionDuffy()
            }
        } finally {
            sh(label: 'remove Duffy home', script: "rm -rf '${duffy_home}'")
        }
    }
}
