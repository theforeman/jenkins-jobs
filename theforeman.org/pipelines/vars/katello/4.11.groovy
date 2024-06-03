def foreman_version = '3.9'
def katello_version = '4.11'
def foreman_el_releases = [
    'el8'
]
def pipelines = [
    'install': [
        'almalinux8',
    ],
    'upgrade': [
        'almalinux8',
    ]
]
