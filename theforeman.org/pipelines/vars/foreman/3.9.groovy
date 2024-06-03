def foreman_version = '3.9'
def git_branch = "${foreman_version}-stable"

def foreman_client_distros = [
    'el9',
    'el8',
    'el7'
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
        'almalinux8',
    ],
    'upgrade': [
        'almalinux8',
    ]
]

def pipelines = [
    'install': pipelines_deb['install'] + pipelines_el['install'],
    'upgrade': pipelines_deb['upgrade'] + pipelines_el['upgrade'],
]
