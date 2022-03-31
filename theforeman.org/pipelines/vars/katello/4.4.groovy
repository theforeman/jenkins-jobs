def foreman_version = '3.2'
def katello_version = '4.4'
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
