\pset pager off
\timing on

-- Production DBA runbook for the 2026-05-17 Flow filter timeout root causes.
--
-- Root cause:
--   The no-ticker raw Flow default query scans the latest resolved trading day,
--   filters by premium/volume/single-leg, and orders by executed_at/trade_id.
--   With option_trades compressed after 2 hours and segmented by ticker,
--   Timescale must scan and bulk-decompress the compressed day chunk before
--   top-N sorting. EXPLAIN ANALYZE on 2026-05-17 measured about 5.64s for the
--   producer query, exceeding the backend 5s timeout.
--
--   After the raw path was fixed, the aggregate ticker/range/expensive load
--   test still produced 503s. EXPLAIN ANALYZE showed the aggregate query using
--   the plain last_executed_at index and filtering about 1.49M latest-day rows
--   to return the first 101 SPY/QQQ OI/OTM rows.
--
--   A threshold-dependent aggregate partial index fixes literal EXPLAIN output
--   but can be missed by Hibernate prepared statements once PostgreSQL uses a
--   generic plan. The aggregate index below keeps only non-parameter predicates
--   in the partial predicate and puts ticker/date/order columns in the key.
--
-- Intended effect:
--   1. Keep recent chunks uncompressed for user-facing latest-day Flow reads.
--   2. Add a partial index matching the hot raw default slice predicate/order.
--   3. Add a partial index matching the aggregate volume/open-interest slice.
--   4. Decompress currently compressed chunks in the hot window.
--   5. Analyze and capture before/after EXPLAIN output for the failing shapes.
--
-- Operator notes:
--   Run with the PG18 admin user through the documented tunnel.
--   This is intentionally not wrapped in one transaction because Timescale
--   policy helpers, decompression, and index creation can be long-running DDL.

SET lock_timeout = '5s';
SET statement_timeout = '45min';
SET idle_in_transaction_session_timeout = '60s';

\echo '1. Current option_trades policy'
SELECT hypertable_schema, hypertable_name, proc_name, schedule_interval, config, next_start
FROM timescaledb_information.jobs
WHERE hypertable_name = 'option_trades'
ORDER BY proc_name;

\echo '2. Add hot raw default slice partial index'
CREATE INDEX IF NOT EXISTS
  idx_option_trades_hot_raw_default_slice
  ON option_trades (executed_at DESC, trade_id DESC)
  WHERE is_multileg = false
    AND premium >= 50000
    AND volume >= 100;

\echo '3. Add generic-plan-safe aggregate OI expensive slice partial index'
CREATE INDEX CONCURRENTLY IF NOT EXISTS
  idx_option_trade_aggregates_oi_ticker_slice
  ON option_trade_aggregates (ticker, last_executed_at DESC, aggregate_id DESC)
  WHERE is_multileg = false
    AND volume IS NOT NULL
    AND open_interest IS NOT NULL
    AND volume > open_interest::bigint
    AND total_size IS NOT NULL
    AND total_size > open_interest::bigint;

\echo '4. Extend option_trades compression policy to 7 days'
DO $$
BEGIN
  IF COALESCE(current_setting('timescaledb.license', true), '') = 'apache' THEN
    RAISE NOTICE 'Skipping option_trades compression policy update because timescaledb.license=apache';
  ELSE
    PERFORM remove_compression_policy('option_trades', if_exists => true);
    PERFORM add_compression_policy('option_trades', INTERVAL '7 days', if_not_exists => true);
  END IF;
END
$$;

\echo '5. Decompress option_trades chunks still inside the 7-day hot window'
DO $$
DECLARE
  target_chunk regclass;
BEGIN
  FOR target_chunk IN
    SELECT format('%I.%I', chunks.chunk_schema, chunks.chunk_name)::regclass
    FROM timescaledb_information.chunks chunks
    WHERE hypertable_schema = 'public'
      AND hypertable_name = 'option_trades'
      AND is_compressed
      AND range_end > now() - INTERVAL '7 days'
    ORDER BY range_start
  LOOP
    RAISE NOTICE 'Decompressing %', target_chunk;
    PERFORM decompress_chunk(target_chunk, if_compressed => true);
  END LOOP;
END
$$;

\echo '6. Analyze hot-path tables'
ANALYZE option_trades;
ANALYZE option_trade_aggregates;

\echo '7. Updated option_trades policy'
SELECT hypertable_schema, hypertable_name, proc_name, schedule_interval, config, next_start
FROM timescaledb_information.jobs
WHERE hypertable_name = 'option_trades'
ORDER BY proc_name;

\echo '8. Recent chunk compression status'
SELECT chunk_schema, chunk_name, range_start, range_end, is_compressed
FROM timescaledb_information.chunks
WHERE hypertable_schema = 'public'
  AND hypertable_name = 'option_trades'
ORDER BY range_start DESC
LIMIT 10;

\echo '9. Verify raw hot-path index presence'
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'option_trades'
  AND indexname IN (
    'idx_option_trades_hot_raw_default_slice',
    'idx_option_trades_executed_premium_volume',
    'idx_option_trades_executed_at'
  )
ORDER BY indexname;

\echo '10. Verify aggregate hot-path index presence'
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'option_trade_aggregates'
  AND indexname IN (
    'idx_option_trade_aggregates_oi_ticker_slice',
    'idx_option_trade_aggregates_ticker_last_executed',
    'idx_option_trade_aggregates_last_executed'
  )
ORDER BY indexname;

\echo '11. EXPLAIN ANALYZE exact failing raw-hot-default query'
SET statement_timeout = '30s';
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)
SELECT trade_id, ticker, price, size, nbbo_bid, nbbo_ask, underlying_price, exchange, executed_at,
       option_chain, condition, is_multileg, is_floor_trade, is_sweep, aggressor, premium, side,
       expiry, option_type, open_interest, strike, volume, ewma_nbbo_ask, ewma_nbbo_bid,
       implied_volatility, delta, theta, gamma, vega, rho, theo, ask_vol, bid_vol, no_side_vol,
       mid_vol, multi_vol, stock_multi_vol, created_at
FROM option_trades
WHERE executed_at >= TIMESTAMPTZ '2026-05-15T04:00:00Z'
  AND executed_at <= TIMESTAMPTZ '2026-05-16T03:59:59.999999Z'
  AND premium >= 50000
  AND volume >= 100
  AND is_multileg = false
ORDER BY executed_at DESC, trade_id DESC
LIMIT 101;

\echo '12. EXPLAIN ANALYZE exact failing aggregate-expensive-oi-otm query'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)
SELECT aggregate_id, grouping_version, ticker, option_chain, side, aggressor, condition_signature,
       is_sweep, is_floor_trade, is_multileg, first_executed_at, last_executed_at,
       representative_price, min_price, max_price, total_size, total_premium, trade_count,
       exchange_count, expiry, option_type, strike, underlying_price_first, underlying_price_last,
       open_interest, volume, created_at, updated_at
FROM option_trade_aggregates
WHERE ticker IN ('SPY','QQQ')
  AND last_executed_at >= TIMESTAMPTZ '2026-05-15T04:00:00Z'
  AND last_executed_at <= TIMESTAMPTZ '2026-05-16T03:59:59.999999Z'
  AND total_premium >= 50000
  AND volume >= 100
  AND is_multileg = false
  AND volume IS NOT NULL
  AND open_interest IS NOT NULL
  AND volume > open_interest::bigint
  AND total_size IS NOT NULL
  AND total_size > open_interest::bigint
  AND strike IS NOT NULL
  AND strike <> 0
  AND option_type IS NOT NULL
  AND (underlying_price_last IS NOT NULL OR underlying_price_first IS NOT NULL)
  AND NOT (
      (lower(option_type) = 'call' AND COALESCE(underlying_price_last, underlying_price_first) >= strike)
      OR (lower(option_type) = 'put' AND COALESCE(underlying_price_last, underlying_price_first) <= strike)
  )
ORDER BY last_executed_at DESC, aggregate_id DESC
LIMIT 101;

\echo '13. EXPLAIN ANALYZE aggregate query as a forced generic prepared plan'
BEGIN;
SET LOCAL plan_cache_mode = force_generic_plan;
PREPARE aggregate_expensive_prepared(text[], timestamptz, timestamptz, numeric, bigint) AS
SELECT aggregate_id, grouping_version, ticker, option_chain, side, aggressor, condition_signature,
       is_sweep, is_floor_trade, is_multileg, first_executed_at, last_executed_at,
       representative_price, min_price, max_price, total_size, total_premium, trade_count,
       exchange_count, expiry, option_type, strike, underlying_price_first, underlying_price_last,
       open_interest, volume, created_at, updated_at
FROM option_trade_aggregates
WHERE ticker = ANY ($1)
  AND last_executed_at >= $2
  AND last_executed_at <= $3
  AND total_premium >= $4
  AND volume >= $5
  AND is_multileg = false
  AND volume IS NOT NULL
  AND open_interest IS NOT NULL
  AND volume > open_interest::bigint
  AND total_size IS NOT NULL
  AND total_size > open_interest::bigint
  AND strike IS NOT NULL
  AND strike <> 0
  AND option_type IS NOT NULL
  AND (underlying_price_last IS NOT NULL OR underlying_price_first IS NOT NULL)
  AND NOT (
      (lower(option_type) = 'call' AND COALESCE(underlying_price_last, underlying_price_first) >= strike)
      OR (lower(option_type) = 'put' AND COALESCE(underlying_price_last, underlying_price_first) <= strike)
  )
ORDER BY last_executed_at DESC, aggregate_id DESC
LIMIT 101;
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)
EXECUTE aggregate_expensive_prepared(
  ARRAY['SPY','QQQ'],
  TIMESTAMPTZ '2026-05-15T04:00:00Z',
  TIMESTAMPTZ '2026-05-16T03:59:59.999999Z',
  50000,
  100
);
ROLLBACK;
