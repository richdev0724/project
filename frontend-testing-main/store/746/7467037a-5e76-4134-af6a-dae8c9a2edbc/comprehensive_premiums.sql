ATTACH TABLE _ UUID 'cc1958fe-7870-4a9e-9703-6024ff2a3506'
(
    `ticker` String,
    `date` Date,
    `total_call_premium` Int64,
    `total_put_premium` Int64,
    `net_call_premium` Int64,
    `net_put_premium` Int64,
    `calculated_net_premium` Int64,
    `total_ticks` UInt32,
    `created_at` DateTime DEFAULT now(),
    `updated_at` DateTime DEFAULT now()
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(date)
ORDER BY (ticker, date)
SETTINGS index_granularity = 8192
