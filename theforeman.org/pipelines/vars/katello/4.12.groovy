def foreman_version = '3.10'
def katello_version = '4.12'
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
        'almalinux8',
    ]
]
