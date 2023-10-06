def candlepin_version = '4.3'
def candlepin_distros = ['el8']
def packaging_branch = 'rpm/4.3'
def pipelines = [
    'candlepin': [
        'centos8-stream'
    ]
]
