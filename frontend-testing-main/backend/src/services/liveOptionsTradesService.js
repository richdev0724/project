const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;

/**
 * Get all options trades for today from Polygon's Options Trades API
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @returns {Promise<object>} - Live options trades data with premiums
 */
async function getLiveOptionsTrades(ticker) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Fetching live options trades for ${ticker}...`);
    
    // Get today's date in YYYY-MM-DD format
    const today = new Date().toISOString().split('T')[0];
    
    // Use the Options Trades API to get all trades for today
    const response = await axios.get(`https://api.polygon.io/v3/trades/${ticker}`, {
      params: {
        apiKey: POLYGON_API_KEY,
        timestamp: today,
        limit: 50000 // Maximum limit
      }
    });

    if (response.data.status !== 'OK') {
      throw new Error(`Polygon API error: ${response.data.status}`);
    }

    const tradesData = response.data.results;
    
    if (!tradesData || tradesData.length === 0) {
      console.log(`No options trades found for ${ticker} today`);
      return {
        ticker,
        date: today,
        totalCalls: 0,
        totalPuts: 0,
        netPremium: 0,
        callVolume: 0,
        putVolume: 0,
        trades: []
      };
    }

    // Filter for options trades only (ticker starts with 'O:')
    const optionsTrades = tradesData.filter(trade => 
      trade.ticker && trade.ticker.startsWith('O:')
    );

    console.log(`Found ${optionsTrades.length} options trades for ${ticker}`);

    // Calculate totals
    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let totalCallVolume = 0;
    let totalPutVolume = 0;
    const trades = [];

    optionsTrades.forEach(trade => {
      const price = parseFloat(trade.price || 0) || 0;
      const size = parseInt(trade.size || 0) || 0;
      
      // Calculate total premium (price * size * 100 shares per contract)
      const totalPremium = price * size * 100;
      
      // Determine if it's a call or put based on ticker
      const isCall = trade.ticker.includes('C');
      const isPut = trade.ticker.includes('P');
      
      const tradeData = {
        ticker: trade.ticker,
        price: price,
        size: size,
        totalPremium: totalPremium,
        timestamp: trade.sip_timestamp,
        exchange: trade.exchange,
        type: isCall ? 'call' : (isPut ? 'put' : 'unknown')
      };

      trades.push(tradeData);

      if (isCall) {
        totalCallPremium += totalPremium;
        totalCallVolume += size;
      } else if (isPut) {
        totalPutPremium += totalPremium;
        totalPutVolume += size;
      }
    });

    const netPremium = totalCallPremium - totalPutPremium;

    console.log(`${ticker} Live Options Trades Summary:`);
    console.log(`- Total Trades: ${optionsTrades.length}`);
    console.log(`- Call Premium: $${totalCallPremium.toLocaleString()}`);
    console.log(`- Put Premium: $${totalPutPremium.toLocaleString()}`);
    console.log(`- Net Premium: $${netPremium.toLocaleString()}`);
    console.log(`- Call Volume: ${totalCallVolume.toLocaleString()}`);
    console.log(`- Put Volume: ${totalPutVolume.toLocaleString()}`);

    return {
      ticker,
      date: today,
      totalCalls: Math.round(totalCallPremium),
      totalPuts: Math.round(totalPutPremium),
      netPremium: Math.round(netPremium),
      callVolume: totalCallVolume,
      putVolume: totalPutVolume,
      trades: trades.slice(0, 100) // Limit to first 100 trades for response size
    };

  } catch (error) {
    console.error(`Error fetching live options trades for ${ticker}:`, error.message);
    throw error;
  }
}

/**
 * Get live net premiums from actual trades for a ticker
 * @param {string} ticker - Stock ticker symbol
 * @returns {Promise<object>} - Live net premiums data from trades
 */
async function getLiveNetPremiumsFromTrades(ticker) {
  try {
    const liveData = await getLiveOptionsTrades(ticker);
    
    return {
      ticker,
      timeframe: '1D',
      data: [{
        name: 'Today',
        calls: liveData.totalCalls,
        puts: liveData.totalPuts,
        callVolume: liveData.callVolume,
        putVolume: liveData.putVolume,
        netPremium: liveData.netPremium
      }],
      totalCalls: liveData.totalCalls,
      totalPuts: liveData.totalPuts,
      netPremium: liveData.netPremium
    };

  } catch (error) {
    console.error(`Error getting live net premiums from trades for ${ticker}:`, error.message);
    return {
      ticker,
      timeframe: '1D',
      data: [],
      totalCalls: 0,
      totalPuts: 0,
      netPremium: 0
    };
  }
}

module.exports = {
  getLiveOptionsTrades,
  getLiveNetPremiumsFromTrades
};
