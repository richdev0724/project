ATTACH TABLE _ UUID 'bcbcda88-ddee-468e-937d-53bab80f7c54'
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
