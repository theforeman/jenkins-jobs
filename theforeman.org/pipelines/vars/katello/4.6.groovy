def foreman_version = '3.4'
def katello_version = '4.6'
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
