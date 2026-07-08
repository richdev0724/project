const axios = require('axios');

const FMP_API_KEY = process.env.FMP_API_KEY;
const BASE_URL = 'https://financialmodelingprep.com/api/v3';

class AnalystRatingsService {
  async getAnalystRatings(ticker) {
    try {
      const response = await axios.get(`${BASE_URL}/rating/${ticker}`, {
        params: {
          apikey: FMP_API_KEY,
        },
      });
      return response.data;
    } catch (error) {
      console.error(`Error fetching analyst ratings for ${ticker}:`, error.message);
      throw error;
    }
  }

  async getPriceTarget(ticker) {
    try {
      const response = await axios.get(`${BASE_URL}/price-target`, {
        params: {
          symbol: ticker.toUpperCase(),
          apikey: FMP_API_KEY,
        },
      });
      return response.data;
    } catch (error) {
      console.error(`Error fetching price target for ${ticker}:`, error.message);
      throw error;
    }
  }

  async getUpgradesDowngrades(ticker) {
    try {
      const response = await axios.get(`${BASE_URL}/upgrades-downgrades`, {
        params: {
          symbol: ticker.toUpperCase(),
          apikey: FMP_API_KEY,
        },
      });
      return response.data;
    } catch (error) {
      console.error(`Error fetching upgrades/downgrades for ${ticker}:`, error.message);
      throw error;
    }
  }
}

module.exports = new AnalystRatingsService();

