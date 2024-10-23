def foreman_version = '3.11'
def git_branch = "${foreman_version}-stable"

def foreman_client_distros = [
    'el9',
    'el8',
    'el7'
]
def foreman_el_releases = [
    'el9',
    'el8'
]
def foreman_debian_releases = ['bullseye', 'bookworm', 'focal', 'jammy']

def pipelines_deb = [
    'install': [
        'debian11',
        'debian12',
        'ubuntu2004',
        'ubuntu2204'
    ],
    'upgrade': [
        'debian11',
        'ubuntu2004'
    ]
]

def pipelines_el = [
    'install': [
        'centos9-stream',
        'almalinux8',
        'almalinux9',
    ],
    'upgrade': [
        'centos9-stream',
        'almalinux8',
        'almalinux9',
    ]
]

def pipelines = [
    'install': pipelines_deb['install'] + pipelines_el['install'],
    'upgrade': pipelines_deb['upgrade'] + pipelines_el['upgrade'],
]
