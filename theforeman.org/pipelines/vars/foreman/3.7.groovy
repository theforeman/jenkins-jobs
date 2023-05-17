def foreman_version = '3.7'
def git_branch = "${foreman_version}-stable"
def foreman_client_distros = [
    'el9',
    'el8',
    'el7',
    'sles12',
    'sles11'
]
def foreman_el_releases = [
    'el8'
]
def foreman_debian_releases = ['bullseye', 'focal']

def pipelines_deb = [
    'install': [
        'debian11',
        'ubuntu2004'
    ],
    'upgrade': [
        'debian11',
        'ubuntu2004'
    ]
]

def pipelines_el = [
    'install': [
        'centos8-stream',
        'almalinux8',
    ],
    'upgrade': [
        'centos8-stream',
        'almalinux8',
    ]
]

def pipelines = [
    'install': pipelines_deb['install'] + pipelines_el['install'],
    'upgrade': pipelines_deb['upgrade'] + pipelines_el['upgrade'],
]
