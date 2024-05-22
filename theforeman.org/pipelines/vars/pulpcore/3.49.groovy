def pulpcore_version = '3.49'
def pulpcore_distros = ['el8', 'el9']
def packaging_branch = 'rpm/3.49'
def pipelines = [
    'pulpcore': [
        'centos8-stream',
        'centos9-stream'
    ]
]
