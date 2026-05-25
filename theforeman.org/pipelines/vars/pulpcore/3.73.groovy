def pulpcore_version = '3.73'
def pulpcore_distros = ['el9']
def packaging_branch = 'rpm/3.73'
def pipelines = [
    'pulpcore': [
        'centos9-stream',
    ]
]
