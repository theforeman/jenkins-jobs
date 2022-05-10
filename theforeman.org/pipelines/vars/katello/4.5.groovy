def foreman_version = '3.3'
def katello_version = '4.5'
def foreman_el_releases = [
    'el7',
    'el8'
]
def pipelines = [
    'install': [
        'almalinux8',
        'centos7',
        'centos8-stream',
    ],
    'upgrade': [
        'almalinux8',
        'centos7',
        'centos8-stream',
    ]
]
