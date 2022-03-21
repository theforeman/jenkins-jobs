def foreman_version = 'nightly'
def katello_version = 'nightly'
def foreman_el_releases = [
    'el7',
    'el8'
]
def pipelines = [
    'install': [
        'centos7',
        'centos8-stream',
        'almalinux8',
    ],
    'upgrade': [
        'centos7',
        'centos8-stream',
        'almalinux8',
    ]
]
