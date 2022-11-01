def foreman_integration_versions = [
    'develop': [
        'ruby': ['2.7'],
        'katello': 'master'
    ],
    '3.5-stable': [
        'ruby': ['2.7'],
        'katello': 'KATELLO-4.7'
    ],
    '3.4-stable': [
        'ruby': ['2.7'],
        'katello': 'KATELLO-4.6'
    ],
    '3.3-stable': [
        'ruby': ['2.7'],
        'katello': 'KATELLO-4.5'
    ],
    '3.2-stable': [
        'ruby': ['2.7'],
        'katello': 'KATELLO-4.4'
    ],
    '3.1-stable': [
        'ruby': ['2.7'],
        'katello': 'KATELLO-4.3'
    ]
]

def foreman_unit_versions = [
    'develop': [
        'ruby': ['2.7']
    ],
    '3.5-stable': [
        'ruby': ['2.7']
    ],
    '3.4-stable': [
        'ruby': ['2.7']
    ],
    '3.3-stable': [
        'ruby': ['2.7', '2.5']
    ],
    '3.2-stable': [
        'ruby': ['2.7', '2.5']
    ],
    '3.1-stable': [
        'ruby': ['2.7', '2.5']
    ]
]

