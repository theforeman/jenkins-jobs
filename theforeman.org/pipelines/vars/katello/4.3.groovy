def foreman_version = '3.1'
def katello_version = '4.3'
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
