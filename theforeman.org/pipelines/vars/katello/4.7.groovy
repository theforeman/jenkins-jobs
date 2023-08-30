def foreman_version = '3.5'
def katello_version = '4.7'
def stage_source = 'koji'
def foreman_el_releases = [
    'el8'
]
def pipelines = [
    'install': [
        'almalinux8',
        'centos8-stream',
    ],
    'upgrade': [
        'almalinux8',
        'centos8-stream',
    ]
]
