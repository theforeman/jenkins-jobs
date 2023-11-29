def foreman_version = '3.9'
def katello_version = '4.11'
def stage_source = 'stagingyum'
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
