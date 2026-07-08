/**
 * Dark Pool & Lit Pool Storage Service
 * Handles ClickHouse storage and retrieval of DP/LP price levels
 */

const { createClient } = require('@clickhouse/client');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '..', '.env') });

class DarkPoolLitPoolStorageService {
  constructor() {
    this.clickhouse = createClient({
      url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
      username: process.env.CLICKHOUSE_USER || 'default',
      password: process.env.CLICKHOUSE_PASSWORD || '',
      database: process.env.CLICKHOUSE_DATABASE || 'options'
    });

    this.darkPoolTable = 'dark_pool_levels';
    this.litPoolTable = 'lit_pool_levels';
    this.initialized = false;
  }

  /**
   * Initialize both tables
   */
  async initialize() {
    if (this.initialized) return;

    try {
      // Dark Pool table
      await this.clickhouse.exec({
        query: `
          CREATE TABLE IF NOT EXISTS ${this.darkPoolTable} (
            ticker String,
            trade_date Date,
            price Float64,
            size UInt64,
            total_premium Float64,
            trades UInt32,
            market_session String,
            created_at DateTime DEFAULT now(),
            updated_at DateTime DEFAULT now()
          ) ENGINE = MergeTree()
          PARTITION BY toYYYYMM(trade_date)
          ORDER BY (ticker, trade_date, total_premium)
          SETTINGS index_granularity = 8192
        `
      });

      // Lit Pool table
      await this.clickhouse.exec({
        query: `
          CREATE TABLE IF NOT EXISTS ${this.litPoolTable} (
            ticker String,
            trade_date Date,
            price Float64,
            size UInt64,
            total_premium Float64,
            trades UInt32,
            market_session String,
            created_at DateTime DEFAULT now(),
            updated_at DateTime DEFAULT now()
          ) ENGINE = MergeTree()
          PARTITION BY toYYYYMM(trade_date)
          ORDER BY (ticker, trade_date, total_premium)
          SETTINGS index_granularity = 8192
        `
      });

      console.log('✅ Dark Pool & Lit Pool tables initialized');
      this.initialized = true;
    } catch (error) {
      console.error('❌ Error initializing DP/LP tables:', error.message);
      throw error;
    }
  }

  /**
   * Store dark pool levels (batch insert)
   * @param {Array} levels - Array of level objects
   */
  async storeDarkPoolLevels(levels) {
    try {
      await this.initialize();

      if (!levels || levels.length === 0) {
        console.log('No dark pool levels to store');
        return { success: true, inserted: 0 };
      }

      // Transform data for ClickHouse
      const values = levels.map(level => ({
        ticker: level.ticker,
        trade_date: level.trade_date,
        price: level.price,
        size: level.size,
        total_premium: level.total_premium,
        trades: level.trades,
        market_session: level.market_session
      }));

      await this.clickhouse.insert({
        table: this.darkPoolTable,
        values,
        format: 'JSONEachRow'
      });

      console.log(`✅ Stored ${values.length} dark pool levels`);
      return { success: true, inserted: values.length };
    } catch (error) {
      console.error('❌ Error storing dark pool levels:', error.message);
      return { success: false, error: error.message };
    }
  }

  /**
   * Store lit pool levels (batch insert)
   * @param {Array} levels - Array of level objects
   */
  async storeLitPoolLevels(levels) {
    try {
      await this.initialize();

      if (!levels || levels.length === 0) {
        console.log('No lit pool levels to store');
        return { success: true, inserted: 0 };
      }

      // Transform data for ClickHouse
      const values = levels.map(level => ({
        ticker: level.ticker,
        trade_date: level.trade_date,
        price: level.price,
        size: level.size,
        total_premium: level.total_premium,
        trades: level.trades,
        market_session: level.market_session
      }));

      await this.clickhouse.insert({
        table: this.litPoolTable,
        values,
        format: 'JSONEachRow'
      });

      console.log(`✅ Stored ${values.length} lit pool levels`);
      return { success: true, inserted: values.length };
    } catch (error) {
      console.error('❌ Error storing lit pool levels:', error.message);
      return { success: false, error: error.message };
    }
  }

  /**
   * Get dark pool levels for a ticker
   * @param {string} ticker - Stock symbol
   * @param {number} days - Number of days to retrieve (default: 1 for today)
   * @param {number} limit - Max levels to return (default: 10)
   */
  async getDarkPoolLevels(ticker, days = 1, limit = 10) {
    try {
      await this.initialize();

      const query = `
        SELECT 
          ticker,
          trade_date,
          price,
          size,
          total_premium,
          trades,
          market_session
        FROM ${this.darkPoolTable}
        WHERE ticker = {ticker:String}
          AND trade_date >= today() - {days:UInt32}
        ORDER BY total_premium DESC
        LIMIT {limit:UInt32}
      `;

      const resultSet = await this.clickhouse.query({
        query,
        query_params: { ticker, days, limit },
        format: 'JSONEachRow'
      });

      const data = await resultSet.json();
      return data;
    } catch (error) {
      console.error('❌ Error fetching dark pool levels:', error.message);
      throw error;
    }
  }

  /**
   * Get lit pool levels for a ticker
   * @param {string} ticker - Stock symbol
   * @param {number} days - Number of days to retrieve (default: 1 for today)
   * @param {number} limit - Max levels to return (default: 10)
   */
  async getLitPoolLevels(ticker, days = 1, limit = 10) {
    try {
      await this.initialize();

      const query = `
        SELECT 
          ticker,
          trade_date,
          price,
          size,
          total_premium,
          trades,
          market_session
        FROM ${this.litPoolTable}
        WHERE ticker = {ticker:String}
          AND trade_date >= today() - {days:UInt32}
        ORDER BY total_premium DESC
        LIMIT {limit:UInt32}
      `;

      const resultSet = await this.clickhouse.query({
        query,
        query_params: { ticker, days, limit },
        format: 'JSONEachRow'
      });

      const data = await resultSet.json();
      return data;
    } catch (error) {
      console.error('❌ Error fetching lit pool levels:', error.message);
      throw error;
    }
  }

  /**
   * Delete old data (cleanup)
   * @param {number} daysToKeep - Keep data from last N days
   */
  async cleanupOldData(daysToKeep = 30) {
    try {
      await this.initialize();

      const cutoffDate = `today() - ${daysToKeep}`;

      await this.clickhouse.exec({
        query: `
          ALTER TABLE ${this.darkPoolTable} 
          DELETE WHERE trade_date < ${cutoffDate}
        `
      });

      await this.clickhouse.exec({
        query: `
          ALTER TABLE ${this.litPoolTable} 
          DELETE WHERE trade_date < ${cutoffDate}
        `
      });

      console.log(`✅ Cleaned up data older than ${daysToKeep} days`);
    } catch (error) {
      console.error('❌ Error cleaning up old data:', error.message);
    }
  }

  /**
   * Check if data exists for a ticker on a specific date
   * @param {string} ticker - Stock symbol
   * @param {string} date - Date in YYYY-MM-DD format
   * @param {string} type - 'dark' or 'lit'
   */
  async hasDataForDate(ticker, date, type = 'dark') {
    try {
      await this.initialize();
      
      const table = type === 'dark' ? this.darkPoolTable : this.litPoolTable;
      
      const query = `
        SELECT count() as count
        FROM ${table}
        WHERE ticker = {ticker:String}
          AND trade_date = {date:Date}
      `;

      const resultSet = await this.clickhouse.query({
        query,
        query_params: { ticker, date },
        format: 'JSONEachRow'
      });

      const data = await resultSet.json();
      return data[0]?.count > 0;
    } catch (error) {
      console.error('❌ Error checking data existence:', error.message);
      return false;
    }
  }

  /**
   * Close ClickHouse connection
   */
  async close() {
    try {
      await this.clickhouse.close();
      console.log('✅ ClickHouse connection closed');
    } catch (error) {
      console.error('❌ Error closing ClickHouse connection:', error.message);
    }
  }
}

module.exports = new DarkPoolLitPoolStorageService();

