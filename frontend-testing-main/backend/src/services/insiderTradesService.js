const axios = require('axios');

const FMP_API_KEY = process.env.FMP_API_KEY;
const BASE_URL = 'https://financialmodelingprep.com/api/v4';

class InsiderTradesService {
  async getInsiderTrades(ticker, limit = 50) {
    try {
      const response = await axios.get(`${BASE_URL}/insider-trading`, {
        params: {
          symbol: ticker.toUpperCase(),
          limit: limit,
          apikey: FMP_API_KEY
        }
      });

      if (!response.data) {
        return [];
      }

      // Format and return the data
      return response.data.map(trade => ({
        filingDate: trade.filingDate,
        transactionDate: trade.transactionDate,
        reportingName: trade.reportingName,
        typeOfOwner: trade.typeOfOwner,
        transactionType: trade.transactionType,
        securitiesOwned: trade.securitiesOwned,
        securitiesTransacted: trade.securitiesTransacted,
        price: trade.price,
        securityName: trade.securityName,
        link: trade.link
      }));
    } catch (error) {
      console.error(`Error fetching insider trades for ${ticker}:`, error.message);
      throw error;
    }
  }
}

module.exports = new InsiderTradesService();

