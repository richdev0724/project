const { createClient } = require('@clickhouse/client');
require('dotenv').config();

class CompanyProfileStorageService {
  constructor() {
    this.clickhouse = createClient({
      url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
      username: process.env.CLICKHOUSE_USER || 'default',
      password: process.env.CLICKHOUSE_PASSWORD || '',
      database: process.env.CLICKHOUSE_DATABASE || 'options'
    });
    
    this.tableName = 'company_profiles';
    this.initialized = false;
  }

  /**
   * Initialize the company profiles table
   */
  async initialize() {
    if (this.initialized) return;

    try {
      await this.clickhouse.exec(`
        CREATE TABLE IF NOT EXISTS ${this.tableName} (
          ticker String,
          company_name String,
          description String,
          industry String,
          sector String,
          ceo String,
          website String,
          employees UInt32,
          market_cap UInt64,
          country String,
          city String,
          state String,
          address String,
          phone String,
          exchange String,
          currency String,
          is_etf UInt8,
          is_actively_trading UInt8,
          created_at DateTime DEFAULT now(),
          updated_at DateTime DEFAULT now()
        ) ENGINE = MergeTree()
        ORDER BY ticker
        SETTINGS index_granularity = 8192
      `);

      console.log(`✅ Company profiles table initialized: ${this.tableName}`);
      this.initialized = true;
    } catch (error) {
      console.error('Error initializing company profiles table:', error.message);
      throw error;
    }
  }

  /**
   * Store a single company profile
   * @param {Object} profile - Company profile data
   * @returns {Promise<boolean>} - Success status
   */
  async storeCompanyProfile(profile) {
    try {
      await this.initialize();

      const profileData = {
        ticker: profile.ticker || '',
        company_name: profile.companyName || '',
        description: profile.description || '',
        industry: profile.industry || '',
        sector: profile.sector || '',
        ceo: profile.ceo || '',
        website: profile.website || '',
        employees: profile.employees || 0,
        market_cap: profile.marketCap || 0,
        country: profile.country || '',
        city: profile.city || '',
        state: profile.state || '',
        address: profile.address || '',
        phone: profile.phone || '',
        exchange: profile.exchange || '',
        currency: profile.currency || 'USD',
        is_etf: profile.isEtf ? 1 : 0,
        is_actively_trading: profile.isActivelyTrading ? 1 : 0,
        updated_at: new Date()
      };

      // Use INSERT with ON DUPLICATE KEY UPDATE equivalent for ClickHouse
      await this.clickhouse.insert({
        table: this.tableName,
        values: [profileData],
        format: 'JSONEachRow'
      });

      return true;
    } catch (error) {
      console.error(`Error storing company profile for ${profile.ticker}:`, error.message);
      return false;
    }
  }

  /**
   * Store multiple company profiles in batch
   * @param {Array} profiles - Array of company profile data
   * @returns {Promise<Object>} - Batch storage results
   */
  async storeCompanyProfilesBatch(profiles) {
    try {
      await this.initialize();

      const profileData = profiles.map(profile => ({
        ticker: profile.ticker || '',
        company_name: profile.companyName || '',
        description: profile.description || '',
        industry: profile.industry || '',
        sector: profile.sector || '',
        ceo: profile.ceo || '',
        website: profile.website || '',
        employees: profile.employees || 0,
        market_cap: profile.marketCap || 0,
        country: profile.country || '',
        city: profile.city || '',
        state: profile.state || '',
        address: profile.address || '',
        phone: profile.phone || '',
        exchange: profile.exchange || '',
        currency: profile.currency || 'USD',
        is_etf: profile.isEtf ? 1 : 0,
        is_actively_trading: profile.isActivelyTrading ? 1 : 0,
        updated_at: new Date()
      }));

      await this.clickhouse.insert({
        table: this.tableName,
        values: profileData,
        format: 'JSONEachRow'
      });

      console.log(`✅ Stored ${profiles.length} company profiles in ClickHouse`);
      return {
        success: true,
        count: profiles.length,
        profiles: profiles.map(p => p.ticker)
      };
    } catch (error) {
      console.error('Error storing company profiles batch:', error.message);
      return {
        success: false,
        error: error.message,
        count: 0
      };
    }
  }

  /**
   * Get company profile by ticker
   * @param {string} ticker - Stock ticker symbol
   * @returns {Promise<Object|null>} - Company profile data
   */
  async getCompanyProfile(ticker) {
    try {
      await this.initialize();

      const result = await this.clickhouse.query({
        query: `SELECT * FROM ${this.tableName} WHERE ticker = {ticker:String}`,
        query_params: { ticker: ticker.toUpperCase() }
      });

      const data = await result.json();
      return data.length > 0 ? data[0] : null;
    } catch (error) {
      console.error(`Error getting company profile for ${ticker}:`, error.message);
      return null;
    }
  }

  /**
   * Get all company profiles
   * @param {Object} options - Query options
   * @returns {Promise<Array>} - Array of company profiles
   */
  async getAllCompanyProfiles(options = {}) {
    try {
      await this.initialize();

      const { limit = 1000, offset = 0, orderBy = 'ticker' } = options;
      
      const result = await this.clickhouse.query({
        query: `
          SELECT * FROM ${this.tableName} 
          ORDER BY ${orderBy} 
          LIMIT {limit:UInt32} OFFSET {offset:UInt32}
        `,
        query_params: { limit, offset }
      });

      return await result.json();
    } catch (error) {
      console.error('Error getting all company profiles:', error.message);
      return [];
    }
  }

  /**
   * Get company profiles count
   * @returns {Promise<number>} - Total number of company profiles
   */
  async getCompanyProfilesCount() {
    try {
      await this.initialize();

      const result = await this.clickhouse.query({
        query: `SELECT COUNT(*) as count FROM ${this.tableName}`
      });

      const data = await result.json();
      return data[0]?.count || 0;
    } catch (error) {
      console.error('Error getting company profiles count:', error.message);
      return 0;
    }
  }

  /**
   * Search company profiles by name or ticker
   * @param {string} searchTerm - Search term
   * @returns {Promise<Array>} - Array of matching company profiles
   */
  async searchCompanyProfiles(searchTerm) {
    try {
      await this.initialize();

      const result = await this.clickhouse.query({
        query: `
          SELECT * FROM ${this.tableName} 
          WHERE ticker ILIKE {search:String} 
             OR company_name ILIKE {search:String}
          ORDER BY ticker
          LIMIT 100
        `,
        query_params: { search: `%${searchTerm}%` }
      });

      return await result.json();
    } catch (error) {
      console.error('Error searching company profiles:', error.message);
      return [];
    }
  }

  /**
   * Update company profile
   * @param {string} ticker - Stock ticker symbol
   * @param {Object} updates - Fields to update
   * @returns {Promise<boolean>} - Success status
   */
  async updateCompanyProfile(ticker, updates) {
    try {
      await this.initialize();

      // ClickHouse doesn't support UPDATE directly, so we'll use ALTER TABLE
      const updateFields = Object.keys(updates).map(key => {
        const value = updates[key];
        if (typeof value === 'string') {
          return `${key} = '${value.replace(/'/g, "''")}'`;
        } else if (typeof value === 'boolean') {
          return `${key} = ${value ? 1 : 0}`;
        } else {
          return `${key} = ${value}`;
        }
      }).join(', ');

      await this.clickhouse.exec(`
        ALTER TABLE ${this.tableName} 
        UPDATE ${updateFields}, updated_at = now()
        WHERE ticker = '${ticker.toUpperCase()}'
      `);

      return true;
    } catch (error) {
      console.error(`Error updating company profile for ${ticker}:`, error.message);
      return false;
    }
  }

  /**
   * Delete company profile
   * @param {string} ticker - Stock ticker symbol
   * @returns {Promise<boolean>} - Success status
   */
  async deleteCompanyProfile(ticker) {
    try {
      await this.initialize();

      await this.clickhouse.exec(`
        ALTER TABLE ${this.tableName} 
        DELETE WHERE ticker = '${ticker.toUpperCase()}'
      `);

      return true;
    } catch (error) {
      console.error(`Error deleting company profile for ${ticker}:`, error.message);
      return false;
    }
  }

  /**
   * Get storage statistics
   * @returns {Promise<Object>} - Storage statistics
   */
  async getStorageStats() {
    try {
      await this.initialize();

      const countResult = await this.clickhouse.query({
        query: `SELECT COUNT(*) as count FROM ${this.tableName}`
      });
      const countData = await countResult.json();

      const sizeResult = await this.clickhouse.query({
        query: `
          SELECT 
            formatReadableSize(sum(data_compressed_bytes)) as compressed_size,
            formatReadableSize(sum(data_uncompressed_bytes)) as uncompressed_size
          FROM system.parts 
          WHERE table = '${this.tableName}' AND active = 1
        `
      });
      const sizeData = await sizeResult.json();

      return {
        totalProfiles: countData[0]?.count || 0,
        compressedSize: sizeData[0]?.compressed_size || '0 B',
        uncompressedSize: sizeData[0]?.uncompressed_size || '0 B',
        tableName: this.tableName
      };
    } catch (error) {
      console.error('Error getting storage stats:', error.message);
      return {
        totalProfiles: 0,
        compressedSize: '0 B',
        uncompressedSize: '0 B',
        tableName: this.tableName
      };
    }
  }
}

module.exports = new CompanyProfileStorageService();
