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

def konflux_gate_rebuild_snapshots(app, rebuildStartedAt) {
    def json = sh(
        label: "resolve rebuilt snapshots: ${app}",
        script: "${konflux_oc_bin()} get snapshot --request-timeout=30s -n ${konflux_namespace()} -l appstudio.openshift.io/application=${app} --sort-by=.metadata.creationTimestamp -o json",
        returnStdout: true
    )
    return readJSON(text: json, returnPojo: true).items.findAll { snapshot ->
        def startedAt = snapshot.metadata.annotations?.get('test.appstudio.openshift.io/pipelinerunstarttime')
        snapshot.metadata.labels?.get('pac.test.appstudio.openshift.io/event-type') == 'incoming' &&
            startedAt && startedAt.toLong() >= rebuildStartedAt.toLong() * 1000
    }
}

def konflux_gate_wait_for_new_snapshot(app, components, previous, rebuildStartedAt, timeoutMinutes) {
    def resolved = ''
    def rebuiltImages = [:]
    // A polling deadline leaves Jenkins cancellations and enclosing timeouts
    // untouched; only reaching this deadline permits the released-image fallback.
    def deadline = System.currentTimeMillis() + timeoutMinutes * 60_000L
    waitUntil {
        if (System.currentTimeMillis() >= deadline) {
            return true
        }
        try {
            def snapshots = konflux_gate_rebuild_snapshots(app, rebuildStartedAt)
            components.each { component ->
                if (!rebuiltImages[component]) {
                    // Only the triggering component's image proves that its build finished.
                    def built = snapshots.find { snapshot ->
                        snapshot.metadata.labels['appstudio.openshift.io/component'] == component
                    }
                    def image = built?.spec?.components?.find { it.name == component }?.containerImage
                    if (image) {
                        rebuiltImages[component] = image
                        echo "${component}: rebuilt image=${image}"
                    }
                }
            }
            if (!components.every { rebuiltImages[it] }) {
                return false
            }

            def complete = snapshots.reverse().find { snapshot ->
                snapshot.metadata.name != previous && components.every { component ->
                    snapshot.spec.components.find { it.name == component }?.containerImage == rebuiltImages[component]
                }
            }
            resolved = complete?.metadata?.name ?: ''
            return resolved as boolean
        } catch (hudson.AbortException ex) {
            echo "Unable to resolve a complete rebuilt Konflux Snapshot for '${app}'; retrying: ${ex.message}"
            return false
        }
    }
    if (resolved) {
        return [app: app, snapshot: resolved, stale: false]
    }
    echo "Timed out waiting for a Konflux Snapshot with all rebuilt components for '${app}' after ${timeoutMinutes}m; falling back to the last released Snapshot"
    def fallback = konflux_gate_last_released_snapshot(app)
    return [app: app, snapshot: fallback, stale: true]
}

def konflux_gate_image_ref(snapshot, component) {
    return sh(
        label: "resolve image ref: ${component}@${snapshot}",
        script: "${konflux_oc_bin()} get snapshot ${snapshot} -n ${konflux_namespace()} -o jsonpath=\"{.spec.components[?(@.name=='${component}')].containerImage}\"",
        returnStdout: true
    ).trim()
}

def konflux_gate_releases(snapshot, releasePlan = null) {
    def json = sh(
        label: "check existing release: ${snapshot}",
        script: "${konflux_oc_bin()} get release -n ${konflux_namespace()} -l release.appstudio.openshift.io/snapshot=${snapshot} -o json",
        returnStdout: true
    )
    return readJSON(text: json, returnPojo: true).items.findAll { release ->
        !releasePlan || release.spec.releasePlan == releasePlan
    }
}

def konflux_gate_find_existing_release(snapshot, releasePlan = null) {
    def releases = konflux_gate_releases(snapshot, releasePlan)
    def succeeded = releases.find { release ->
        release.status?.conditions?.any { it.type == 'Released' && it.status == 'True' }
    }
    def pending = releases.find { release ->
        !release.status?.conditions?.any { it.type == 'Released' && it.status == 'False' && it.reason == 'Failed' }
    }
    return (succeeded ?: pending)?.metadata?.name ?: ''
}

def konflux_gate_image_published(image, repositories) {
    def digest = image.tokenize('@')[-1]
    if (!(digest ==~ /sha256:[0-9a-f]{64}/) || !repositories) {
        error("Cannot check publication of '${image}' without a digest and destination repositories")
    }
    return repositories.every { repository ->
        if (!(repository ==~ /quay\.io\/[a-z0-9._-]+\/[a-z0-9._\/-]+/)) {
            error("Unsupported Quay destination repository '${repository}'")
        }
        def status = sh(
            label: "check published image: ${repository}@${digest}",
            script: "curl -sS --retry 3 --connect-timeout 10 --max-time 30 -o /dev/null -w '%{http_code}' 'https://quay.io/api/v1/repository/${repository.substring(8)}/manifest/${digest}'",
            returnStdout: true
        ).trim()
        if (!(status in ['200', '404'])) {
            error("Unable to check publication of '${repository}@${digest}': Quay returned HTTP ${status}")
        }
        return status == '200'
    }
}

def konflux_gate_existing_snapshot(app, components, releasePlan) {
    // These are the integration service's accepted staging images, not production
    // release destinations. A stable stream can update one component at a time.
    def componentJson = sh(
        label: "resolve accepted component images: ${app}",
        script: "${konflux_oc_bin()} get component ${components.join(' ')} -n ${konflux_namespace()} -o json",
        returnStdout: true
    )
    def componentData = readJSON(text: componentJson, returnPojo: true)
    def componentItems = componentData.items != null ? componentData.items : [componentData]
    def images = [:]
    componentItems.each { component ->
        images[component.metadata.name] = component.status?.lastPromotedImage
    }

    def snapshotJson = sh(
        label: "resolve existing snapshots: ${app}",
        script: "${konflux_oc_bin()} get snapshot -n ${konflux_namespace()} -l appstudio.openshift.io/application=${app} --sort-by=.metadata.creationTimestamp -o json",
        returnStdout: true
    )
    def snapshots = readJSON(text: snapshotJson, returnPojo: true).items
    def snapshot = snapshots.reverse().find { candidate ->
        candidate.metadata.labels?.get('pac.test.appstudio.openshift.io/event-type') in ['push', 'incoming'] &&
            components.every { component ->
                images[component] && candidate.spec.components.find { it.name == component }?.containerImage == images[component]
            }
    }
    if (!snapshot) {
        echo "No complete accepted Snapshot available for '${app}'"
        return [app: app, snapshot: '', stale: true]
    }

    def releases = konflux_gate_releases(snapshot.metadata.name, releasePlan)
    if (releases.any { release -> release.status?.conditions?.any { it.type == 'Released' && it.status == 'True' } }) {
        return [app: app, snapshot: snapshot.metadata.name, stale: true]
    }

    def planJson = sh(
        label: "resolve release destinations: ${releasePlan}",
        script: "${konflux_oc_bin()} get releaseplan ${releasePlan} -n ${konflux_namespace()} -o json",
        returnStdout: true
    )
    def plan = readJSON(text: planJson, returnPojo: true)
    if (plan.spec.application != app) {
        error("ReleasePlan '${releasePlan}' does not target application '${app}'")
    }
    def published = components.every { component ->
        def mapping = plan.spec.data.mapping.components.find { it.name == component }
        def repositories = mapping?.repositories?.collect { it.url } ?: (mapping?.repository ? [mapping.repository] : [])
        konflux_gate_image_published(images[component], repositories)
    }
    return [app: app, snapshot: snapshot.metadata.name, stale: published]
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
    def released = null
    def deadline = System.currentTimeMillis() + timeoutMinutes * 60_000L
    def settled = false
    waitUntil {
        if (System.currentTimeMillis() >= deadline) {
            return true
        }
        def json = sh(
            label: "poll release status: ${releaseName}",
            script: "${konflux_oc_bin()} get release ${releaseName} --request-timeout=30s -n ${konflux_namespace()} -o json",
            returnStdout: true
        )
        released = readJSON(text: json, returnPojo: true).status?.conditions?.find { it.type == 'Released' }
        // Konflux also uses False with reason Progressing while a release
        // is running. Only Failed is a terminal unsuccessful outcome.
        settled = released?.status == 'True' || (released?.status == 'False' && released?.reason == 'Failed')
        return settled
    }
    if (!settled) {
        echo "Timed out after ${timeoutMinutes}m waiting for Release '${releaseName}' to settle"
        return null
    }

    return [succeeded: released.status == 'True', reason: released.reason, message: released.message]
}

def konflux_gate_release_artifacts(releaseName) {
    def json = sh(
        label: "release artifacts: ${releaseName}",
        script: "${konflux_oc_bin()} get release ${releaseName} -n ${konflux_namespace()} -o jsonpath='{.status.artifacts.images}'",
        returnStdout: true
    ).trim()
    return readJSON(text: json ?: '[]')
}

def konflux_gate_run_test(imageRefs, foremanctlBranch, pytestArgs) {
    def boxname = 'duffy_box'
    def var_prefix = konflux_gate_image_var_prefix()
    def duffy_tmp = pwd(tmp: true)
    def imageVars = [:]
    imageRefs.each { component, ref ->
        def prefix = var_prefix[component]
        if (!prefix) {
            error("konflux_gate_run_test: no foremanctl image variables known for component '${component}'")
        }
        if (!(ref ==~ /[a-z0-9._:\/-]+@sha256:[0-9a-f]{64}/)) {
            error("konflux_gate_run_test: expected a digest-pinned image for '${component}', got '${ref}'")
        }
        def parts = ref.tokenize('@')
        imageVars["${prefix}_container_image"] = "${parts[0]}@sha256"
        imageVars["${prefix}_container_tag"] = parts[1].substring(7)
    }
    def imageArgs = "--extra-vars '${writeJSON(returnText: true, json: imageVars)}'"
    def foremanctl = 'OBSAH_ALLOW_EXTRA_VARS=true ./foremanctl'
    def duffy_home = ''

    try {
        withEnv(["DUFFY_TMP=${duffy_tmp}"]) {
            duffy_home = sh(label: 'create Duffy home', script: 'mkdir -p "$DUFFY_TMP" && umask 077 && mktemp -d "$DUFFY_TMP/duffy-home.XXXXXX"', returnStdout: true).trim()
        }
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
                    playbook: 'jenkins-jobs/centos.org/ansible/setup_vagrant_libvirt.yml',
                    inventory: duffy_inventory('./'),
                    limit: "duffy_session_${duffy_session}",
                    options: ['-b'],
                )
            }

            duffy_ssh("git clone --depth 1 --branch '${foremanctlBranch}' https://github.com/theforeman/foremanctl.git", boxname, './')
            duffy_ssh('cd foremanctl && GITHUB_ACTIONS=true ./setup-environment', boxname, './')
        }

        try {
            stage('Deploy and test') {
                duffy_ssh('cd foremanctl && ./forge vms start', boxname, './')
                duffy_ssh('cd foremanctl && ./forge setup-repositories', boxname, './')
                duffy_ssh("cd foremanctl && ${foremanctl} pull-images ${imageArgs}", boxname, './')
                duffy_ssh("cd foremanctl && ${foremanctl} deploy ${imageArgs} --initial-admin-password=changeme --initial-organization \"Foreman CI\" --initial-location \"Internet\" --tuning development --content-import-path /custom/import --content-export-path /custom/export", boxname, './')
                duffy_ssh("cd foremanctl && ${foremanctl} deploy ${imageArgs} --add-feature hammer --add-feature foreman-proxy --add-feature azure-rm --add-feature google --add-feature remote-execution --add-feature ansible --add-feature bmc --add-feature webhooks", boxname, './')
                imageRefs.each { component, ref ->
                    def imageName = var_prefix[component].replace('_', '-')
                    duffy_ssh("cd foremanctl && vagrant ssh quadlet -c \"sudo grep -Fx 'Image=${ref}' /etc/containers/systemd/${imageName}.image\"", boxname, './')
                }
                duffy_ssh("cd foremanctl && ${foremanctl} health ${imageArgs}", boxname, './')
                duffy_ssh("cd foremanctl && ./forge test --pytest-args=\"${pytestArgs}\"", boxname, './')
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
        if (duffy_home) {
            try {
                withEnv(["HOME=${duffy_home}"]) {
                    deprovisionDuffy()
                }
            } finally {
                withEnv(["DUFFY_HOME=${duffy_home}"]) {
                    sh(label: 'remove Duffy home', script: 'rm -rf -- "$DUFFY_HOME"')
                }
            }
        }
    }
}
