def foreman_version = '5.1'
def katello_version = '5.1'
def konflux_components = ['candlepin-5-1', 'foreman-5-1', 'foreman-proxy-5-1', 'pulp-5-1']
def konflux_gate_job_name = 'konflux-gate-5.1-pipeline'
def konflux_gate_rebuild = false
def konflux_gate_foremanctl_branch = '3.4-stable'
def konflux_gate_pytest_args = ''
def konflux_gate_applications = [
    'candlepin-5-1': ['candlepin-5-1'],
    'foreman-5-1': ['foreman-5-1', 'foreman-proxy-5-1'],
    'pulp-5-1': ['pulp-5-1'],
]
def konflux_gate_release_plans = [
    'candlepin-5-1': 'release-candlepin-5-1',
    'foreman-5-1': 'release-foreman-5-1',
    'pulp-5-1': 'release-pulp-5-1',
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
