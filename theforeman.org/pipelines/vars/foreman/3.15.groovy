def foreman_version = '3.15'
def git_branch = "${foreman_version}-stable"
def ruby_version = '2.7.6'

def foreman_client_distros = [
    'el10',
    'el9',
    'el8',
]
def foreman_el_releases = [
    'el9'
]
def foreman_debian_releases = ['bookworm', 'jammy']

def pipelines_deb = [
    'install': [
        'debian12',
        'ubuntu2204'
    ],
    'upgrade': [
        'debian12',
        'ubuntu2204'
    ]
]

def pipelines_el = [
    'install': [
        'centos9-stream',
        'almalinux9',
    ],
    'upgrade': [
        'centos9-stream',
        'almalinux9',
    ]
]

def pipelines = [
    'install': pipelines_deb['install'] + pipelines_el['install'],
    'upgrade': pipelines_deb['upgrade'] + pipelines_el['upgrade'],
]
