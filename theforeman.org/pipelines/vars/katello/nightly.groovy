def foreman_version = 'nightly'
def katello_version = 'nightly'
def stage_source = 'stagingyum'
def foreman_el_releases = [
    'el8',
    'el9'
]
def pipelines = [
    'install': [
        'centos8-stream',
        'centos9-stream',
        'almalinux8',
        'almalinux9',
    ],
    'upgrade': [
        'centos8-stream',
        'centos9-stream',
        'almalinux8',
        'almalinux9',
    ]
]
