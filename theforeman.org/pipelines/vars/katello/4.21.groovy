def foreman_version = '3.19'
def katello_version = '4.21'
def foreman_el_releases = [
    'el9'
]
def pipelines = [
    'install': [
        'centos9-stream',
    ],
    'upgrade': [
        'centos9-stream',
    ]
]
