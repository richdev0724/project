ATTACH TABLE _ UUID 'f86f6710-31eb-4f71-9b3d-839996b22854'
(
    `ticker` String,
    `trade_date` Date,
    `price` Float64,
    `size` UInt64,
    `total_premium` Float64,
    `trades` UInt32,
    `market_session` String,
    `created_at` DateTime DEFAULT now(),
    `updated_at` DateTime DEFAULT now()
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(trade_date)
ORDER BY (ticker, trade_date, total_premium)
SETTINGS index_granularity = 8192
