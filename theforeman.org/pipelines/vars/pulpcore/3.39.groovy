def pulpcore_version = '3.39'
def pulpcore_distros = ['el8', 'el9']
def packaging_branch = 'rpm/3.39'
def pipelines = [
    'pulpcore': [
        'centos8-stream',
        'centos9-stream'
    ]
]
def stage_source = 'stagingyum'
