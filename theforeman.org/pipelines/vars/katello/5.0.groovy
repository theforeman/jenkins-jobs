def foreman_version = '5.0'
def katello_version = '5.0'
def konflux_components = ['candlepin-5-0', 'foreman-5-0', 'foreman-proxy-5-0', 'pulp-5-0']
def konflux_gate_job_name = 'konflux-gate-5.0-pipeline'
def konflux_gate_applications = [
    'candlepin-5-0': ['candlepin-5-0'],
    'foreman-5-0': ['foreman-5-0', 'foreman-proxy-5-0'],
    'pulp-5-0': ['pulp-5-0'],
]
def konflux_gate_release_plans = [
    'candlepin-5-0': 'release-candlepin-5-0-production',
    'foreman-5-0': 'release-foreman-5-0-production',
    'pulp-5-0': 'release-pulp-5-0-production',
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
