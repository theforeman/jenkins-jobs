def foreman_version = '2.5'
def katello_version = '4.1'
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
