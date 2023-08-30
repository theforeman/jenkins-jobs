def foreman_version = '3.7'
def katello_version = '4.9'
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
