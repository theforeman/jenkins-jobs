def foreman_version = 'nightly'
def katello_version = 'nightly'
def konflux_components = ['candlepin-develop', 'foreman-develop', 'foreman-proxy-develop', 'pulp-develop']
def foreman_el_releases = [
    'el10',
    'el9'
]
def pipelines = [
    'install': [
        'centos10-stream',
        'almalinux10',
        'centos9-stream',
        'almalinux9',
    ],
    'upgrade': [
        'centos10-stream',
        'almalinux10',
        'centos9-stream',
        'almalinux9',
    ]
]
