def foreman_version = '5.0'
def katello_version = '5.0'
def konflux_components = ['candlepin-5-0', 'foreman-5-0', 'foreman-proxy-5-0', 'pulp-5-0']
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
