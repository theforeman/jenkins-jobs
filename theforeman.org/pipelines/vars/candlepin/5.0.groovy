def candlepin_version = '5.0'
def packaging_branch = 'rpm/5.0'
def candlepin_distros = [
    'el10'
]
def pipelines = [
    'candlepin': [
        'centos10-stream',
        'almalinux10',
    ]
]
