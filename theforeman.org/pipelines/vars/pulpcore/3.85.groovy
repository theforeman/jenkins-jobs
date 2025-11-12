def pulpcore_version = 'nightly'
def pulpcore_distros = ['el9']
def packaging_branch = 'rpm/3.85'
def pipelines = [
    'pulpcore': [
        'centos9-stream',
        'almalinux9',
    ]
]
