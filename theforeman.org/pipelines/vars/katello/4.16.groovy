def foreman_version = '3.14'
def katello_version = '4.16'
def foreman_el_releases = [
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
