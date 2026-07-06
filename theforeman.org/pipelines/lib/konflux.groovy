def konflux_api_server = 'https://api.kflux-fedora-01.84db.p1.openshiftapps.com:6443'
def konflux_namespace = 'theforeman-org-tenant'
def konflux_oc_channel = 'stable'

def retrigger_konflux_components(components) {
    if (!components) {
        return
    }

    def oc_dir = "${env.WORKSPACE}/bin"
    def oc_bin = "${oc_dir}/oc"

    sh(
        label: 'download oc client',
        script: """
            mkdir -p ${oc_dir}
            curl -fsSL --retry 3 https://mirror.openshift.com/pub/openshift-v4/clients/ocp/${konflux_oc_channel}/openshift-client-linux.tar.gz | tar -xz -C ${oc_dir} oc
            chmod +x ${oc_bin}
        """
    )

    try {
        withCredentials([string(credentialsId: 'konflux-jenkins-trigger-token', variable: 'KONFLUX_TOKEN')]) {
            sh(
                label: 'oc login to konflux (token hidden)',
                script: """
                    set +x
                    ${oc_bin} login --token=\$KONFLUX_TOKEN --server=${konflux_api_server}
                    set -x
                """
            )
        }

        components.each { component ->
            sh(
                label: "trigger konflux rebuild: ${component}",
                script: "${oc_bin} annotate component ${component} --overwrite --namespace ${konflux_namespace} build.appstudio.openshift.io/request=trigger-rebuild"
            )
        }
    } finally {
        sh(label: 'remove oc client', script: "rm -rf ${oc_dir}")
    }
}
