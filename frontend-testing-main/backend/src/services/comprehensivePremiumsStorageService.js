const { createClient } = require('@clickhouse/client');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '..', '.env') });

class ComprehensivePremiumsStorageService {
  constructor() {
    this.clickhouse = createClient({
      url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
      username: process.env.CLICKHOUSE_USER || 'default',
      password: process.env.CLICKHOUSE_PASSWORD || '',
      database: process.env.CLICKHOUSE_DATABASE || 'options'
    });
  }

  /**
   * Create the comprehensive premiums table if it doesn't exist
   */
  async createTable() {
    try {
      const createTableQuery = `
        CREATE TABLE IF NOT EXISTS comprehensive_premiums (
          ticker String,
          date Date,
          total_call_premium Int64,
          total_put_premium Int64,
          net_call_premium Int64,
          net_put_premium Int64,
          calculated_net_premium Int64,
          total_ticks UInt32,
          created_at DateTime DEFAULT now(),
          updated_at DateTime DEFAULT now()
        ) ENGINE = MergeTree()
        ORDER BY (ticker, date)
        PARTITION BY toYYYYMM(date)
      `;

      await this.clickhouse.exec({ query: createTableQuery });
      console.log('✅ Comprehensive premiums table created/verified');
    } catch (error) {
      console.error('❌ Error creating comprehensive premiums table:', error.message);
      throw error;
    }
  }

  /**
   * Clear all data for a specific ticker
   * @param {string} ticker - Stock ticker symbol
   */
  async clearTickerData(ticker) {
    try {
      const deleteQuery = `
        ALTER TABLE comprehensive_premiums DELETE 
        WHERE ticker = '${ticker.toUpperCase()}'
      `;

      await this.clickhouse.exec({ query: deleteQuery });
      console.log(`✅ Cleared all comprehensive premiums data for ${ticker.toUpperCase()}`);
    } catch (error) {
      console.error(`❌ Error clearing data for ${ticker}:`, error.message);
      throw error;
    }
  }

  /**
   * Store comprehensive premium data for a single day
   * @param {Object} data - Comprehensive premium data
   */
  async storeComprehensivePremiumData(data) {
    try {
      const insertQuery = `
        INSERT INTO comprehensive_premiums (
          ticker,
          date,
          total_call_premium,
          total_put_premium,
          net_call_premium,
          net_put_premium,
          calculated_net_premium,
          total_ticks,
          created_at,
          updated_at
        ) VALUES (
          '${data.ticker}',
          '${data.date}',
          ${data.totalCallPremium},
          ${data.totalPutPremium},
          ${data.netCallPremium},
          ${data.netPutPremium},
          ${data.calculatedNetPremium},
          ${data.totalTicks},
          now(),
          now()
        )
      `;

      await this.clickhouse.exec({ query: insertQuery });
      console.log(`✅ Stored comprehensive premium data for ${data.ticker} on ${data.date}`);
    } catch (error) {
      console.error(`❌ Error storing comprehensive premium data:`, error.message);
      throw error;
    }
  }

  /**
   * Store multiple days of comprehensive premium data
   * @param {Array} dataArray - Array of comprehensive premium data
   */
  async storeMultipleDaysData(dataArray) {
    try {
      if (dataArray.length === 0) {
        console.log('⚠️ No data to store');
        return;
      }

      const values = dataArray.map(data => 
        `('${data.ticker}', '${data.date}', ${data.totalCallPremium}, ${data.totalPutPremium}, ${data.netCallPremium}, ${data.netPutPremium}, ${data.calculatedNetPremium}, ${data.totalTicks}, now(), now())`
      ).join(', ');

      const insertQuery = `
        INSERT INTO comprehensive_premiums (
          ticker,
          date,
          total_call_premium,
          total_put_premium,
          net_call_premium,
          net_put_premium,
          calculated_net_premium,
          total_ticks,
          created_at,
          updated_at
        ) VALUES ${values}
      `;

      await this.clickhouse.exec({ query: insertQuery });
      console.log(`✅ Stored ${dataArray.length} days of comprehensive premium data for ${dataArray[0].ticker}`);
    } catch (error) {
      console.error(`❌ Error storing multiple days data:`, error.message);
      throw error;
    }
  }

  /**
   * Get comprehensive premium data for a ticker
   * @param {string} ticker - Stock ticker symbol
   * @param {number} days - Number of days to retrieve (default: 15)
   * @returns {Promise<Array>} - Array of comprehensive premium data
   */
  async getComprehensivePremiumData(ticker, days = 15) {
    try {
      const selectQuery = `
        SELECT 
          ticker,
          date,
          total_call_premium,
          total_put_premium,
          net_call_premium,
          net_put_premium,
          calculated_net_premium,
          total_ticks,
          created_at,
          updated_at
        FROM comprehensive_premiums
        WHERE ticker = '${ticker.toUpperCase()}'
        ORDER BY date DESC
        LIMIT ${days}
      `;

      const result = await this.clickhouse.query({ query: selectQuery, format: 'JSONEachRow' });
      const rows = await result.json();
      
      return rows.map(row => ({
        ticker: row.ticker,
        date: row.date,
        totalCallPremium: row.total_call_premium,
        totalPutPremium: row.total_put_premium,
        netCallPremium: row.net_call_premium,
        netPutPremium: row.net_put_premium,
        calculatedNetPremium: row.calculated_net_premium,
        totalTicks: row.total_ticks,
        createdAt: row.created_at,
        updatedAt: row.updated_at
      }));
    } catch (error) {
      console.error(`❌ Error retrieving comprehensive premium data for ${ticker}:`, error.message);
      throw error;
    }
  }

  /**
   * Get statistics about stored data
   * @returns {Promise<Object>} - Statistics about stored data
   */
  async getStorageStatistics() {
    try {
      const statsQuery = `
        SELECT 
          COUNT(*) as total_records,
          COUNT(DISTINCT ticker) as unique_tickers,
          MIN(date) as earliest_date,
          MAX(date) as latest_date,
          COUNT(DISTINCT date) as unique_dates
        FROM comprehensive_premiums
      `;

      const result = await this.clickhouse.query({ query: statsQuery, format: 'JSONEachRow' });
      const rows = await result.json();
      
      if (rows.length > 0) {
        return {
          totalRecords: rows[0].total_records,
          uniqueTickers: rows[0].unique_tickers,
          earliestDate: rows[0].earliest_date,
          latestDate: rows[0].latest_date,
          uniqueDates: rows[0].unique_dates
        };
      }
      
      return {
        totalRecords: 0,
        uniqueTickers: 0,
        earliestDate: null,
        latestDate: null,
        uniqueDates: 0
      };
    } catch (error) {
      console.error('❌ Error getting storage statistics:', error.message);
      throw error;
    }
  }

  /**
   * Test the connection to ClickHouse
   * @returns {Promise<boolean>} - True if connection is successful
   */
  async testConnection() {
    try {
      await this.clickhouse.query({ query: 'SELECT 1' });
      console.log('✅ ClickHouse connection successful');
      return true;
    } catch (error) {
      console.error('❌ ClickHouse connection failed:', error.message);
      return false;
    }
  }
}

module.exports = new ComprehensivePremiumsStorageService();
