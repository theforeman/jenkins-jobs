def candlepin_version = '4.4'
def candlepin_distros = ['el8', 'el9']
def packaging_branch = 'rpm/4.4'
def pipelines = [
    'candlepin': [
        'centos8-stream',
        'centos9-stream'
    ]
]
