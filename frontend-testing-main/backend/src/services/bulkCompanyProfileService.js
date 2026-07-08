const axios = require('axios');
const allTickersService = require('./allTickersService');
const companyProfileStorageService = require('./companyProfileStorageService');
require('dotenv').config();

const FMP_API_KEY = process.env.FMP_API_KEY;

class BulkCompanyProfileService {
  constructor() {
    this.updateStatus = {
      isRunning: false,
      totalTickers: 0,
      processedTickers: 0,
      successfulUpdates: 0,
      failedUpdates: 0,
      startTime: null,
      endTime: null,
      errors: [],
      currentBatch: 0,
      totalBatches: 0
    };
    
    // Rate limiting configuration based on FMP API research
    this.batchSize = 20; // Reduced batch size to respect rate limits
    this.delayBetweenBatches = 5000; // 5 seconds between batches (allows 4 requests/second)
    this.maxConcurrentRequests = 4; // FMP allows 4 parallel queries per second
    this.requestDelay = 250; // 250ms between individual requests (4 per second)
    this.maxRetries = 3; // Maximum retry attempts for failed requests
    this.baseRetryDelay = 1000; // Base delay for exponential backoff (1 second)
    
    // Rate limiting state
    this.lastRequestTime = 0;
    this.requestQueue = [];
    this.isProcessingQueue = false;
  }

  /**
   * Rate limiting helper - ensures we don't exceed 4 requests per second
   */
  async enforceRateLimit() {
    const now = Date.now();
    const timeSinceLastRequest = now - this.lastRequestTime;
    
    if (timeSinceLastRequest < this.requestDelay) {
      const waitTime = this.requestDelay - timeSinceLastRequest;
      await new Promise(resolve => setTimeout(resolve, waitTime));
    }
    
    this.lastRequestTime = Date.now();
  }

  /**
   * Make API request with exponential backoff retry logic
   * @param {string} ticker - Ticker symbol
   * @param {number} attempt - Current attempt number
   * @returns {Promise<Object|null>} Company profile data or null
   */
  async fetchCompanyProfileWithRetry(ticker, attempt = 1) {
    try {
      await this.enforceRateLimit();
      
      const url = `https://financialmodelingprep.com/api/v3/profile/${ticker}?apikey=${FMP_API_KEY}`;
      const response = await axios.get(url);
      
      if (response.data && response.data.length > 0) {
        return response.data[0];
      }
      return null;
      
    } catch (error) {
      if (error.response && error.response.status === 429) {
        // Rate limit exceeded - implement exponential backoff
        if (attempt <= this.maxRetries) {
          const retryDelay = this.baseRetryDelay * Math.pow(2, attempt - 1);
          const retryAfter = error.response.headers['retry-after'];
          const waitTime = retryAfter ? parseInt(retryAfter) * 1000 : retryDelay;
          
          console.log(`Rate limited for ${ticker}, retrying in ${waitTime}ms (attempt ${attempt}/${this.maxRetries})`);
          await new Promise(resolve => setTimeout(resolve, waitTime));
          return this.fetchCompanyProfileWithRetry(ticker, attempt + 1);
        } else {
          console.error(`Max retries exceeded for ${ticker}: ${error.message}`);
          throw error;
        }
      } else {
        // Other errors (404, 500, etc.) - don't retry
        console.error(`API error for ${ticker}: ${error.message}`);
        throw error;
      }
    }
  }

  /**
   * Update company profiles for all US tickers
   * @param {Object} options - Update options
   * @returns {Promise<Object>} Update results
   */
  async updateAllCompanyProfiles(options = {}) {
    const {
      forceRefresh = false,
      batchSize = this.batchSize, // Now defaults to 20 (rate-limited)
      delayBetweenBatches = this.delayBetweenBatches, // Now defaults to 5000ms
      maxConcurrentRequests = this.maxConcurrentRequests, // Now defaults to 4
      sampleSize = null // For testing with smaller sets
    } = options;

    // Reset status
    this.updateStatus = {
      isRunning: true,
      totalTickers: 0,
      processedTickers: 0,
      successfulUpdates: 0,
      failedUpdates: 0,
      startTime: new Date(),
      endTime: null,
      errors: [],
      currentBatch: 0,
      totalBatches: 0
    };

    try {
      console.log('🚀 Starting bulk company profile update...');
      
      // Get all tickers
      let allTickers;
      if (sampleSize && sampleSize > 0) {
        console.log(`📊 Using sample of ${sampleSize} tickers for testing`);
        allTickers = await allTickersService.getSampleTickers(sampleSize);
      } else {
        console.log('📊 Fetching all US tickers...');
        allTickers = await allTickersService.getAllUSTickers(forceRefresh);
      }

      this.updateStatus.totalTickers = allTickers.length;
      this.updateStatus.totalBatches = Math.ceil(allTickers.length / batchSize);

      console.log(`📈 Found ${allTickers.length} tickers to process in ${this.updateStatus.totalBatches} batches`);

      // Process tickers in batches
      for (let i = 0; i < allTickers.length; i += batchSize) {
        this.updateStatus.currentBatch = Math.floor(i / batchSize) + 1;
        const batch = allTickers.slice(i, i + batchSize);
        
        console.log(`🔄 Processing batch ${this.updateStatus.currentBatch}/${this.updateStatus.totalBatches} (${batch.length} tickers)`);
        
        await this.processBatch(batch, maxConcurrentRequests);
        
        // Delay between batches to be respectful to APIs
        if (i + batchSize < allTickers.length) {
          console.log(`⏳ Waiting ${delayBetweenBatches}ms before next batch...`);
          await new Promise(resolve => setTimeout(resolve, delayBetweenBatches));
        }
      }

      this.updateStatus.endTime = new Date();
      this.updateStatus.isRunning = false;

      const duration = this.updateStatus.endTime - this.updateStatus.startTime;
      console.log(`✅ Bulk update completed!`);
      console.log(`📊 Results: ${this.updateStatus.successfulUpdates} successful, ${this.updateStatus.failedUpdates} failed`);
      console.log(`⏱️ Duration: ${Math.round(duration / 1000)}s`);

      return {
        success: true,
        ...this.updateStatus,
        duration: duration
      };

    } catch (error) {
      this.updateStatus.isRunning = false;
      this.updateStatus.endTime = new Date();
      console.error('❌ Bulk update failed:', error);
      
      return {
        success: false,
        error: error.message,
        ...this.updateStatus
      };
    }
  }

  /**
   * Process a batch of tickers
   * @param {Array} tickers - Array of ticker symbols
   * @param {number} maxConcurrent - Max concurrent requests
   */
  async processBatch(tickers, maxConcurrent) {
    const promises = [];
    let currentIndex = 0;

    // Process tickers in chunks to respect concurrency limits
    while (currentIndex < tickers.length) {
      const chunk = tickers.slice(currentIndex, currentIndex + maxConcurrent);
      
      const chunkPromises = chunk.map(ticker => 
        this.updateSingleCompanyProfile(ticker)
          .catch(error => {
            console.error(`❌ Failed to update ${ticker}:`, error.message);
            return { ticker, success: false, error: error.message };
          })
      );

      const chunkResults = await Promise.allSettled(chunkPromises);
      
      // Process results
      chunkResults.forEach(result => {
        if (result.status === 'fulfilled') {
          const updateResult = result.value;
          this.updateStatus.processedTickers++;
          
          if (updateResult.success) {
            this.updateStatus.successfulUpdates++;
          } else {
            this.updateStatus.failedUpdates++;
            this.updateStatus.errors.push({
              ticker: updateResult.ticker,
              error: updateResult.error,
              timestamp: new Date().toISOString()
            });
          }
        } else {
          this.updateStatus.processedTickers++;
          this.updateStatus.failedUpdates++;
          this.updateStatus.errors.push({
            ticker: 'unknown',
            error: result.reason?.message || 'Unknown error',
            timestamp: new Date().toISOString()
          });
        }
      });

      currentIndex += maxConcurrent;
      
      // Small delay between chunks
      if (currentIndex < tickers.length) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }
  }

  /**
   * Update company profile for a single ticker
   * @param {string} ticker - Ticker symbol
   * @returns {Promise<Object>} Update result
   */
  async updateSingleCompanyProfile(ticker) {
    try {
      if (!FMP_API_KEY) {
        throw new Error('FMP_API_KEY is not configured');
      }

      // Use the new rate-limited method with retry logic
      const profile = await this.fetchCompanyProfileWithRetry(ticker);

      if (!profile) {
        throw new Error('No company profile data found');
      }
      
      // Validate required fields
      if (!profile.symbol || !profile.companyName) {
        throw new Error('Invalid profile data structure');
      }

      // Format profile data for storage
      const formattedProfile = {
        ticker: profile.symbol,
        companyName: profile.companyName,
        description: profile.description || '',
        industry: profile.industry || '',
        sector: profile.sector || '',
        ceo: profile.ceo || '',
        website: profile.website || '',
        employees: profile.fullTimeEmployees || 0,
        marketCap: profile.mktCap || 0,
        country: profile.country || '',
        city: profile.city || '',
        state: profile.state || '',
        address: profile.address || '',
        phone: profile.phone || '',
        exchange: profile.exchangeShortName || '',
        currency: profile.currency || 'USD',
        isEtf: profile.isEtf || false,
        isActivelyTrading: profile.isActivelyTrading || true
      };

      // Store in ClickHouse
      const storageSuccess = await companyProfileStorageService.storeCompanyProfile(formattedProfile);
      
      if (storageSuccess) {
        console.log(`✅ Updated and stored ${ticker}: ${profile.companyName}`);
      } else {
        console.warn(`⚠️ Updated ${ticker}: ${profile.companyName} but failed to store in ClickHouse`);
      }
      
      return {
        ticker,
        success: true,
        stored: storageSuccess,
        profile: formattedProfile
      };

    } catch (error) {
      return {
        ticker,
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get current update status
   * @returns {Object} Current status
   */
  getUpdateStatus() {
    const now = new Date();
    const duration = this.updateStatus.startTime ? 
      (this.updateStatus.endTime || now) - this.updateStatus.startTime : 0;
    
    const progress = this.updateStatus.totalTickers > 0 ? 
      (this.updateStatus.processedTickers / this.updateStatus.totalTickers) * 100 : 0;

    return {
      ...this.updateStatus,
      duration: Math.round(duration / 1000), // Duration in seconds
      progress: Math.round(progress * 100) / 100, // Progress percentage
      estimatedTimeRemaining: this.calculateEstimatedTimeRemaining()
    };
  }

  /**
   * Calculate estimated time remaining
   * @returns {number} Estimated seconds remaining
   */
  calculateEstimatedTimeRemaining() {
    if (!this.updateStatus.isRunning || this.updateStatus.processedTickers === 0) {
      return null;
    }

    const now = new Date();
    const elapsed = now - this.updateStatus.startTime;
    const rate = this.updateStatus.processedTickers / elapsed; // tickers per ms
    const remaining = this.updateStatus.totalTickers - this.updateStatus.processedTickers;
    
    return Math.round(remaining / rate / 1000); // Convert to seconds
  }

  /**
   * Stop the current update process
   */
  stopUpdate() {
    this.updateStatus.isRunning = false;
    this.updateStatus.endTime = new Date();
    console.log('🛑 Bulk update stopped by user');
  }

  /**
   * Get update statistics
   * @returns {Object} Statistics
   */
  getStatistics() {
    const status = this.getUpdateStatus();
    return {
      totalUpdates: this.updateStatus.successfulUpdates + this.updateStatus.failedUpdates,
      successRate: this.updateStatus.processedTickers > 0 ? 
        (this.updateStatus.successfulUpdates / this.updateStatus.processedTickers) * 100 : 0,
      averageTimePerTicker: this.updateStatus.processedTickers > 0 ? 
        status.duration / this.updateStatus.processedTickers : 0,
      lastUpdate: this.updateStatus.endTime,
      errorCount: this.updateStatus.errors.length
    };
  }

  /**
   * Get ClickHouse storage statistics
   * @returns {Promise<Object>} - Storage statistics
   */
  async getStorageStatistics() {
    try {
      return await companyProfileStorageService.getStorageStats();
    } catch (error) {
      console.error('Error getting storage statistics:', error.message);
      return {
        totalProfiles: 0,
        compressedSize: '0 B',
        uncompressedSize: '0 B',
        tableName: 'company_profiles',
        error: error.message
      };
    }
  }

  /**
   * Get company profile from ClickHouse
   * @param {string} ticker - Stock ticker symbol
   * @returns {Promise<Object|null>} - Company profile data
   */
  async getCompanyProfileFromStorage(ticker) {
    try {
      return await companyProfileStorageService.getCompanyProfile(ticker);
    } catch (error) {
      console.error(`Error getting company profile for ${ticker}:`, error.message);
      return null;
    }
  }

  /**
   * Get all company profiles from ClickHouse
   * @param {Object} options - Query options
   * @returns {Promise<Array>} - Array of company profiles
   */
  async getAllCompanyProfilesFromStorage(options = {}) {
    try {
      return await companyProfileStorageService.getAllCompanyProfiles(options);
    } catch (error) {
      console.error('Error getting all company profiles:', error.message);
      return [];
    }
  }
}

module.exports = new BulkCompanyProfileService();
