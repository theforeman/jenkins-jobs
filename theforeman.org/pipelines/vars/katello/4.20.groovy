def foreman_version = '3.18'
def katello_version = '4.20'
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
