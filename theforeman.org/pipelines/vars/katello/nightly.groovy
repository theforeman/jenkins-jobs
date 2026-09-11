def foreman_version = 'nightly'
def katello_version = 'nightly'
def konflux_components = ['candlepin-develop', 'foreman-develop', 'foreman-proxy-develop', 'pulp-develop']
def konflux_gate_job_name = 'konflux-gate-nightly-pipeline'
def konflux_gate_applications = [
    'candlepin': ['candlepin-develop'],
    'foreman': ['foreman-develop', 'foreman-proxy-develop'],
    'pulp': ['pulp-develop'],
]
def konflux_gate_release_plans = [
    'candlepin': 'release-candlepin-develop-nightly',
    'foreman': 'release-foreman-develop-nightly',
    'pulp': 'release-pulp-develop-nightly',
]
def snapshot_wait_timeout_minutes = 60
def release_wait_timeout_minutes = 30
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
