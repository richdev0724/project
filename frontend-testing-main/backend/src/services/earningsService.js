const axios = require('axios');

class EarningsService {
  constructor() {
    this.baseURL = 'https://api.unusualwhales.com/api';
    this.apiKey = process.env.UW_API;
    
    // Cache for earnings data
    this.cache = {
      premarket: { data: null, timestamp: null },
      afterhours: { data: null, timestamp: null }
    };
    
    // Cache TTL: 5 minutes
    this.cacheTTL = 5 * 60 * 1000;
  }
  
  isCacheValid(cacheEntry) {
    if (!cacheEntry.data || !cacheEntry.timestamp) return false;
    return (Date.now() - cacheEntry.timestamp) < this.cacheTTL;
  }
  
  async fetchPremarketData(date) {
    // Create cache key based on date
    const cacheKey = `premarket_${date}`;
    
    if (!this.cache[cacheKey]) {
      this.cache[cacheKey] = { data: null, timestamp: null };
    }
    
    if (this.isCacheValid(this.cache[cacheKey])) {
      console.log(`✅ Using cached premarket data for ${date}`);
      return this.cache[cacheKey].data;
    }
    
    console.log(`📡 Fetching fresh premarket data from API for date: ${date}...`);
    const response = await axios.get(`${this.baseURL}/earnings/premarket`, {
      params: { date },
      headers: {
        'Authorization': `Bearer ${this.apiKey}`,
        'Accept': 'application/json'
      },
      timeout: 15000
    });
    
    const data = response.data?.data || response.data || [];
    this.cache[cacheKey] = {
      data,
      timestamp: Date.now()
    };
    
    return data;
  }
  
  async fetchAfterhoursData(date) {
    // Create cache key based on date
    const cacheKey = `afterhours_${date}`;
    
    if (!this.cache[cacheKey]) {
      this.cache[cacheKey] = { data: null, timestamp: null };
    }
    
    if (this.isCacheValid(this.cache[cacheKey])) {
      console.log(`✅ Using cached afterhours data for ${date}`);
      return this.cache[cacheKey].data;
    }
    
    console.log(`📡 Fetching fresh afterhours data from API for date: ${date}...`);
    const response = await axios.get(`${this.baseURL}/earnings/afterhours`, {
      params: { date },
      headers: {
        'Authorization': `Bearer ${this.apiKey}`,
        'Accept': 'application/json'
      },
      timeout: 15000
    });
    
    const data = response.data?.data || response.data || [];
    this.cache[cacheKey] = {
      data,
      timestamp: Date.now()
    };
    
    return data;
  }

  async getEarningsByDate(date) {
    try {
      if (!this.apiKey) {
        console.error('⚠️ UW_API key is not configured in environment variables');
        return {
          premarket: [],
          afterhours: [],
          error: 'API key not configured'
        };
      }

      console.log(`📅 Fetching earnings for date: ${date}`);

      // Fetch premarket and afterhours data with date parameter
      // These methods use caching internally and the API filters by date
      const premarketData = await this.fetchPremarketData(date);
      const afterhoursData = await this.fetchAfterhoursData(date);

      // Format the data
      const formatEarnings = (earnings) => {
        if (!earnings || !Array.isArray(earnings)) return [];
        
        return earnings.map(item => ({
          symbol: item.symbol,
          full_name: item.full_name || '',
          marketcap: item.marketcap || '0',
          time: item.report_time || item.time
        }));
      };

      const premarket = formatEarnings(premarketData);
      const afterhours = formatEarnings(afterhoursData);

      console.log(`✅ Earnings for ${date}: ${premarket.length} premarket, ${afterhours.length} afterhours`);

      return {
        premarket,
        afterhours
      };
    } catch (error) {
      console.error(`❌ Error fetching earnings for ${date}:`, error.message);
      
      if (error.response) {
        console.error('Response status:', error.response.status);
        console.error('Response data:', error.response.data);
      }

      return {
        premarket: [],
        afterhours: [],
        error: error.message
      };
    }
  }

  async getTickerEarnings(ticker) {
    const cacheKey = `ticker_${ticker}`;
    
    if (!this.cache[cacheKey]) {
      this.cache[cacheKey] = { data: null, timestamp: null };
    }
    
    if (this.isCacheValid(this.cache[cacheKey])) {
      console.log(`✅ Using cached earnings data for ${ticker}`);
      return this.cache[cacheKey].data;
    }

    try {
      if (!this.apiKey) {
        console.error('⚠️ UW_API key is not configured in environment variables');
        return {
          data: [],
          error: 'API key not configured'
        };
      }

      console.log(`📡 Fetching earnings data for ticker: ${ticker}...`);
      const response = await axios.get(`${this.baseURL}/earnings/${ticker}`, {
        headers: {
          'Authorization': `Bearer ${this.apiKey}`,
          'Accept': 'application/json'
        },
        timeout: 15000
      });

      const data = response.data?.data || response.data || [];
      
      this.cache[cacheKey] = {
        data: { data },
        timestamp: Date.now()
      };
      
      console.log(`✅ Fetched ${data.length} earnings records for ${ticker}`);
      return { data };
    } catch (error) {
      console.error(`❌ Error fetching earnings for ${ticker}:`, error.message);
      
      if (error.response) {
        console.error('Response status:', error.response.status);
        console.error('Response data:', error.response.data);
      }

      return {
        data: [],
        error: error.message
      };
    }
  }
}

module.exports = new EarningsService();

