def konflux_api_server() {
    return 'https://api.kflux-fedora-01.84db.p1.openshiftapps.com:6443'
}

def konflux_namespace() {
    return 'theforeman-org-tenant'
}

def konflux_oc_dir() {
    return "${env.WORKSPACE}/bin"
}

def konflux_oc_bin() {
    return "${konflux_oc_dir()}/oc"
}

def konflux_login() {
    def konflux_oc_channel = 'stable'
    def oc_dir = konflux_oc_dir()

    sh(
        label: 'download oc client',
        script: """
            mkdir -p ${oc_dir}
            curl -fsSL --retry 3 https://mirror.openshift.com/pub/openshift-v4/clients/ocp/${konflux_oc_channel}/openshift-client-linux.tar.gz | tar -xz -C ${oc_dir} oc
            chmod +x ${konflux_oc_bin()}
        """
    )

    // Runs on fixed, non-ephemeral 'el' agents, so the kubeconfig is scoped to
    // the workspace (wiped in konflux_logout) instead of the shared ~/.kube/config.
    env.KUBECONFIG = "${oc_dir}/kubeconfig"

    withCredentials([string(credentialsId: 'konflux-jenkins-trigger-token', variable: 'KONFLUX_TOKEN')]) {
        sh(
            label: 'oc login to konflux (token hidden)',
            script: """
                set +x
                ${konflux_oc_bin()} login --token=\$KONFLUX_TOKEN --server=${konflux_api_server()}
                set -x
            """
        )
    }
}

def konflux_logout() {
    sh(label: 'oc logout from konflux', script: "${konflux_oc_bin()} logout || true")
    sh(label: 'remove oc client and kubeconfig', script: "rm -rf ${konflux_oc_dir()}")
    env.KUBECONFIG = null
}

// Shared between katello.groovy (captures the pre-rebuild snapshot per Application
// before triggering, to avoid a race with the konflux-gate-* job) and
// konflux_gate.groovy (polls this for the post-rebuild snapshot).
def konflux_latest_snapshot(app) {
    return sh(
        label: "resolve latest snapshot: ${app}",
        script: "${konflux_oc_bin()} get snapshot -n ${konflux_namespace()} -l appstudio.openshift.io/application=${app} --sort-by=.metadata.creationTimestamp -o jsonpath='{.items[-1].metadata.name}'",
        returnStdout: true
    ).trim()
}

def retrigger_konflux_components(components) {
    if (!components) {
        return
    }

    try {
        konflux_login()

        components.each { component ->
            sh(
                label: "trigger konflux rebuild: ${component}",
                script: "${konflux_oc_bin()} annotate component ${component} --overwrite --namespace ${konflux_namespace()} build.appstudio.openshift.io/request=trigger-pac-build"
            )
        }
    } finally {
        konflux_logout()
    }
}
