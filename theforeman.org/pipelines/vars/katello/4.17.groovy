def foreman_version = '3.15'
def katello_version = '4.17'
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
