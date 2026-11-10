def foreman_version = '5.1'
def katello_version = '5.1'
// Enable the OCI rebuild and gate only after the versioned 5.1 resources are ready.
def konflux_gate_rebuild = false
def konflux_gate_applications = [:]
def foreman_el_releases = [
    'el10',
    'el9'
]
def pipelines = [
    'install': [
        'centos9-stream',
        'almalinux9',
    ],
    'upgrade': [
        'centos9-stream',
        'almalinux9',
    ]
]
