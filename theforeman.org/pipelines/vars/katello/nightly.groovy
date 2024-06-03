def foreman_version = 'nightly'
def katello_version = 'nightly'
def foreman_el_releases = [
    'el8',
    'el9'
]
def pipelines = [
    'install': [
        'centos9-stream',
        'almalinux8',
        'almalinux9',
    ],
    'upgrade': [
        'centos9-stream',
        'almalinux8',
        'almalinux9',
    ]
]
