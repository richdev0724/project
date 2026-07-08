const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;

/**
 * Get all options contracts for a ticker
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @returns {Promise<Array>} - Array of options contracts
 */
async function getAllOptionsContracts(ticker) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Fetching all options contracts for ${ticker}...`);
    
    let allContracts = [];
    let nextUrl = null;
    let pageCount = 0;
    const maxPages = 100; // Safety limit

    // First request
    const initialResponse = await axios.get(`https://api.polygon.io/v3/reference/options/contracts`, {
      params: {
        apiKey: POLYGON_API_KEY,
        underlying_ticker: ticker,
        limit: 1000 // Maximum limit per request
      }
    });

    if (initialResponse.data.status !== 'OK') {
      throw new Error(`Polygon API error: ${initialResponse.data.status}`);
    }

    allContracts = allContracts.concat(initialResponse.data.results || []);
    nextUrl = initialResponse.data.next_url;
    pageCount++;

    console.log(`Page ${pageCount}: Found ${initialResponse.data.results?.length || 0} contracts`);

    // Paginate through all results
    while (nextUrl && pageCount < maxPages) {
      try {
        const response = await axios.get(nextUrl);
        
        if (response.data.status !== 'OK') {
          console.log(`API error on page ${pageCount + 1}: ${response.data.status}`);
          break;
        }

        const newContracts = response.data.results || [];
        allContracts = allContracts.concat(newContracts);
        nextUrl = response.data.next_url;
        pageCount++;

        console.log(`Page ${pageCount}: Found ${newContracts.length} contracts (Total: ${allContracts.length})`);

        // Small delay to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 200));

      } catch (error) {
        console.error(`Error fetching page ${pageCount + 1}:`, error.message);
        break;
      }
    }

    console.log(`Total contracts fetched: ${allContracts.length}`);
    return allContracts;

  } catch (error) {
    console.error(`Error fetching options contracts for ${ticker}:`, error.message);
    throw error;
  }
}

/**
 * Get trades for a specific options contract on a specific date
 * @param {string} contractTicker - Options contract ticker (e.g., 'O:SPY251008C00500000')
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<Array>} - Array of trades
 */
async function getTradesForContract(contractTicker, date) {
  try {
    const response = await axios.get(`https://api.polygon.io/v3/trades/${contractTicker}`, {
      params: {
        apiKey: POLYGON_API_KEY,
        timestamp: date,
        limit: 50000 // Maximum limit
      }
    });

    if (response.data.status !== 'OK') {
      console.log(`No trades found for ${contractTicker} on ${date}: ${response.data.status}`);
      return [];
    }

    return response.data.results || [];

  } catch (error) {
    console.log(`Error fetching trades for ${contractTicker}:`, error.message);
    return [];
  }
}

/**
 * Get complete daily premiums for a ticker by fetching all contracts and their trades
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<object>} - Complete daily premiums data
 */
async function getCompleteDailyPremiums(ticker, date) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Calculating complete daily premiums for ${ticker} on ${date}...`);
    
    // Step 1: Get all options contracts
    const contracts = await getAllOptionsContracts(ticker);
    
    if (contracts.length === 0) {
      return {
        ticker,
        date,
        totalCallPremium: 0,
        totalPutPremium: 0,
        netPremium: 0,
        totalCallVolume: 0,
        totalPutVolume: 0,
        totalContracts: 0,
        totalTrades: 0
      };
    }

    // Step 2: Get trades for each contract
    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let totalCallVolume = 0;
    let totalPutVolume = 0;
    let totalTrades = 0;
    let processedContracts = 0;

    console.log(`Processing ${contracts.length} contracts...`);

    for (const contract of contracts) {
      try {
        const trades = await getTradesForContract(contract.ticker, date);
        
        if (trades.length > 0) {
          console.log(`  ${contract.ticker}: ${trades.length} trades`);
          
          trades.forEach(trade => {
            const price = parseFloat(trade.price || 0) || 0;
            const size = parseInt(trade.size || 0) || 0;
            
            // Calculate total premium (price * size * 100 shares per contract)
            const totalPremium = price * size * 100;
            
            if (contract.contract_type === 'call') {
              totalCallPremium += totalPremium;
              totalCallVolume += size;
            } else if (contract.contract_type === 'put') {
              totalPutPremium += totalPremium;
              totalPutVolume += size;
            }
            
            totalTrades++;
          });
        }
        
        processedContracts++;
        
        // Progress update every 100 contracts
        if (processedContracts % 100 === 0) {
          console.log(`  Processed ${processedContracts}/${contracts.length} contracts...`);
        }
        
        // Small delay to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 50));
        
      } catch (error) {
        console.error(`Error processing contract ${contract.ticker}:`, error.message);
        // Continue with next contract
      }
    }

    const netPremium = totalCallPremium - totalPutPremium;

    console.log(`${ticker} Complete Daily Premiums Summary for ${date}:`);
    console.log(`- Total Contracts: ${contracts.length}`);
    console.log(`- Processed Contracts: ${processedContracts}`);
    console.log(`- Total Trades: ${totalTrades}`);
    console.log(`- Call Premium: $${totalCallPremium.toLocaleString()}`);
    console.log(`- Put Premium: $${totalPutPremium.toLocaleString()}`);
    console.log(`- Net Premium: $${netPremium.toLocaleString()}`);
    console.log(`- Call Volume: ${totalCallVolume.toLocaleString()}`);
    console.log(`- Put Volume: ${totalPutVolume.toLocaleString()}`);

    return {
      ticker,
      date,
      totalCallPremium: Math.round(totalCallPremium),
      totalPutPremium: Math.round(totalPutPremium),
      netPremium: Math.round(netPremium),
      totalCallVolume,
      totalPutVolume,
      totalContracts: contracts.length,
      totalTrades
    };

  } catch (error) {
    console.error(`Error calculating complete daily premiums for ${ticker}:`, error.message);
    throw error;
  }
}

module.exports = {
  getAllOptionsContracts,
  getTradesForContract,
  getCompleteDailyPremiums
};
