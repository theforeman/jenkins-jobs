def candlepin_version = '4.6'
def packaging_branch = 'rpm/4.6'
def candlepin_distros = [
    'el9'
]
def pipelines = [
    'candlepin': [
        'centos9-stream',
        'almalinux9',
    ]
]
