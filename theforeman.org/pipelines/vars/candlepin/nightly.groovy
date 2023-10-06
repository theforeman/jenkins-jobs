def candlepin_version = 'nightly'
def candlepin_distros = ['el8']
def packaging_branch = 'rpm/develop'
def pipelines = [
    'candlepin': [
        'centos8-stream'
    ]
]
