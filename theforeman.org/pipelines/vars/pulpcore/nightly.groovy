def pulpcore_version = 'nightly'
def pulpcore_distros = ['el8', 'el9']
def packaging_branch = 'rpm/develop'
def pipelines = [
    'pulpcore': [
        'centos9-stream',
        'almalinux8',
    ]
]
