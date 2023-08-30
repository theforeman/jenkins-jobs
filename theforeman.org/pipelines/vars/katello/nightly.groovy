def foreman_version = 'nightly'
def katello_version = 'nightly'
def stage_source = 'koji'
def foreman_el_releases = [
    'el8'
]
def pipelines = [
    'install': [
        'centos8-stream',
        'almalinux8',
    ],
    'upgrade': [
        'centos8-stream',
        'almalinux8',
    ]
]
