def foreman_version = '3.0'
def foreman_client_distros = [
    'el8',
    'el7',
    'el6',
    'sles12',
    'sles11'
]
def foreman_el_releases = [
    'el7',
    'el8'
]
def foreman_debian_releases = ['buster', 'bionic', 'focal']

def pipelines_deb = [
    'install': [
        'debian10',
        'ubuntu1804',
        'ubuntu2004'
    ],
    'upgrade': [
        'debian10',
        'ubuntu1804'
    ]
]

def pipelines_el = [
    'install': [
        'centos7',
        'centos8',
        'centos8-stream',
    ],
    'upgrade': [
        'centos7',
        'centos8',
    ]
]

def pipelines = [
    'install': pipelines_deb['install'] + pipelines_el['install'],
    'upgrade': pipelines_deb['upgrade'] + pipelines_el['upgrade'],
]
