def pulpcore_version = 'nightly'
def pulpcore_distros = ['el8', 'el9']
def packaging_branch = 'rpm/develop'
def pipelines = [
    'pulpcore': [
        'centos8-stream',
        'centos9-stream'
    ]
]
def stage_source = 'stagingyum'
