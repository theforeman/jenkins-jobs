def foreman_version = 'nightly'
def katello_version = 'nightly'
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
