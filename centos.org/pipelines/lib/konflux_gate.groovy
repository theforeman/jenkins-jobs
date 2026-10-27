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

def konflux_gate_validate_inputs(imageRefs, components, foremanctlBranch) {
    if (!(imageRefs instanceof Map) || imageRefs.keySet().sort() != components.sort(false)) {
        error('IMAGE_REFS must contain exactly the components for this stream')
    }
    imageRefs.each { component, ref ->
        if (!konflux_gate_image_var_prefix()[component]) {
            error("No foremanctl image variables known for component '${component}'")
        }
        if (!(ref instanceof String) || !(ref ==~ /[a-z0-9._:\/-]+@sha256:[0-9a-f]{64}/)) {
            error("Expected a digest-pinned image for '${component}', got '${ref}'")
        }
    }
    if (!(foremanctlBranch ==~ /[a-zA-Z0-9][a-zA-Z0-9._\/-]*/)) {
        error('FOREMANCTL_BRANCH must be a branch name')
    }
}

def konflux_gate_shell_quote(value) {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

def konflux_gate_test_on_duffy(imageRefs, foremanctlBranch, pytestArgs) {
    def boxname = 'duffy_box'
    def var_prefix = konflux_gate_image_var_prefix()
    def imageVars = [:]
    imageRefs.each { component, ref ->
        def prefix = var_prefix[component]
        def parts = ref.tokenize('@')
        imageVars["${prefix}_container_image"] = "${parts[0]}@sha256"
        imageVars["${prefix}_container_tag"] = parts[1].substring(7)
    }
    def imageArgs = "--extra-vars ${konflux_gate_shell_quote(writeJSON(returnText: true, json: imageVars))}"
    def foremanctl = 'OBSAH_ALLOW_EXTRA_VARS=true ./foremanctl'

    try {
        stage('Provision Duffy node') {
            provisionDuffy()
        }
        stage('Prepare Duffy node') {
            def duffy_session = readFile(file: 'jenkins-jobs/centos.org/ansible/duffy_session').trim()
            runPlaybook(
                playbook: 'jenkins-jobs/centos.org/ansible/setup_vagrant_libvirt.yml',
                inventory: duffy_inventory('./'),
                limit: "duffy_session_${duffy_session}",
                options: ['-b'],
            )

            duffy_ssh("git clone --depth 1 --branch ${konflux_gate_shell_quote(foremanctlBranch)} https://github.com/theforeman/foremanctl.git", boxname, './')
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
                try {
                    def testArgs = konflux_gate_shell_quote("${pytestArgs ?: ''} --junitxml=gate-results.xml".trim())
                    duffy_ssh("cd foremanctl && ./forge test --pytest-args=${testArgs}", boxname, './')
                } finally {
                    // Publish results even when pytest fails. Missing results must also fail the gate.
                    duffy_scp('foremanctl/gate-results.xml', "${env.WORKSPACE}/gate-results.xml", boxname, './')
                    archiveArtifacts artifacts: 'gate-results.xml'
                    junit testResults: 'gate-results.xml'
                }
            }
        } catch (Exception ex) {
            stage('Collect sos reports') {
                try {
                    duffy_ssh('cd foremanctl && ./forge sos', boxname, './')
                    duffy_scp('foremanctl/sos', "${env.WORKSPACE}/sos", boxname, './')
                    archiveArtifacts artifacts: 'sos/**', allowEmptyArchive: true
                } catch (Exception diagnosticsError) {
                    echo "Unable to collect sos reports: ${diagnosticsError.message}"
                }
            }
            throw ex
        }
    } finally {
        deprovisionDuffy()
    }
}
