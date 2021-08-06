def foreman_version = '3.0'
def katello_version = '4.2'
def foreman_el_releases = [
    'el7',
    'el8'
]
def pipelines = [
    'install': [
        'centos7',
        'centos8'
    ],
    'upgrade': [
        'centos7',
        'centos8'
    ]
]
