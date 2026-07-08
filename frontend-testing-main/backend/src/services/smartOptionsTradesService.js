const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;

/**
 * Find options contracts that have trading activity for a specific date
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<Array>} - Array of active options contracts
 */
async function findActiveOptionsContracts(ticker, date) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Finding active options contracts for ${ticker} on ${date}...`);
    
    // Get a sample of options contracts first
    let allContracts = [];
    let nextUrl = null;
    let pageCount = 0;
    const maxPages = 10; // Limit to first 10 pages for efficiency

    // First request
    const initialResponse = await axios.get(`https://api.polygon.io/v3/reference/options/contracts`, {
      params: {
        apiKey: POLYGON_API_KEY,
        underlying_ticker: ticker,
        limit: 1000
      }
    });

    if (initialResponse.data.status !== 'OK') {
      throw new Error(`Polygon API error: ${initialResponse.data.status}`);
    }

    allContracts = allContracts.concat(initialResponse.data.results || []);
    nextUrl = initialResponse.data.next_url;
    pageCount++;

    console.log(`Page ${pageCount}: Found ${initialResponse.data.results?.length || 0} contracts`);

    // Get more contracts
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

        await new Promise(resolve => setTimeout(resolve, 200));

      } catch (error) {
        console.error(`Error fetching page ${pageCount + 1}:`, error.message);
        break;
      }
    }

    console.log(`Total contracts to check: ${allContracts.length}`);

    // Now check which contracts have trading activity
    const activeContracts = [];
    let checkedContracts = 0;

    for (const contract of allContracts) {
      try {
        const trades = await getTradesForContract(contract.ticker, date);
        
        if (trades.length > 0) {
          activeContracts.push({
            ...contract,
            tradeCount: trades.length
          });
          console.log(`  ✅ ${contract.ticker}: ${trades.length} trades`);
        }
        
        checkedContracts++;
        
        // Progress update every 100 contracts
        if (checkedContracts % 100 === 0) {
          console.log(`  Checked ${checkedContracts}/${allContracts.length} contracts, found ${activeContracts.length} active`);
        }
        
        // Small delay to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 50));
        
      } catch (error) {
        console.error(`Error checking contract ${contract.ticker}:`, error.message);
        // Continue with next contract
      }
    }

    console.log(`Found ${activeContracts.length} active contracts out of ${checkedContracts} checked`);
    return activeContracts;

  } catch (error) {
    console.error(`Error finding active options contracts for ${ticker}:`, error.message);
    throw error;
  }
}

/**
 * Get trades for a specific options contract (without date filter)
 * @param {string} contractTicker - Options contract ticker (e.g., 'O:SPY251008C00500000')
 * @param {string} date - Date in YYYY-MM-DD format (for filtering)
 * @returns {Promise<Array>} - Array of trades filtered by date
 */
async function getTradesForContract(contractTicker, date) {
  try {
    // Get recent trades without date filter (since timestamp parameter causes 403)
    const response = await axios.get(`https://api.polygon.io/v3/trades/${contractTicker}`, {
      params: {
        apiKey: POLYGON_API_KEY,
        limit: 50000 // Maximum limit
      }
    });

    if (response.data.status !== 'OK') {
      return [];
    }

    const trades = response.data.results || [];
    
    // Filter trades by date manually
    const targetDate = new Date(date).toISOString().split('T')[0];
    const filteredTrades = trades.filter(trade => {
      if (!trade.sip_timestamp) return false;
      const tradeDate = new Date(trade.sip_timestamp / 1000000).toISOString().split('T')[0];
      return tradeDate === targetDate;
    });

    return filteredTrades;

  } catch (error) {
    console.log(`Error fetching trades for ${contractTicker}:`, error.message);
    return [];
  }
}

/**
 * Get smart daily premiums by finding active contracts first
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<object>} - Smart daily premiums data
 */
async function getSmartDailyPremiums(ticker, date) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Calculating smart daily premiums for ${ticker} on ${date}...`);
    
    // Step 1: Find active contracts
    const activeContracts = await findActiveOptionsContracts(ticker, date);
    
    if (activeContracts.length === 0) {
      return {
        ticker,
        date,
        totalCallPremium: 0,
        totalPutPremium: 0,
        netPremium: 0,
        totalCallVolume: 0,
        totalPutVolume: 0,
        totalContracts: 0,
        activeContracts: 0,
        totalTrades: 0
      };
    }

    // Step 2: Calculate premiums for active contracts only
    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let totalCallVolume = 0;
    let totalPutVolume = 0;
    let totalTrades = 0;

    console.log(`Processing ${activeContracts.length} active contracts...`);

    for (const contract of activeContracts) {
      try {
        const trades = await getTradesForContract(contract.ticker, date);
        
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
        
        // Small delay to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 50));
        
      } catch (error) {
        console.error(`Error processing contract ${contract.ticker}:`, error.message);
        // Continue with next contract
      }
    }

    const netPremium = totalCallPremium - totalPutPremium;

    console.log(`${ticker} Smart Daily Premiums Summary for ${date}:`);
    console.log(`- Total Contracts Checked: ${activeContracts.length}`);
    console.log(`- Active Contracts: ${activeContracts.length}`);
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
      totalContracts: activeContracts.length,
      activeContracts: activeContracts.length,
      totalTrades
    };

  } catch (error) {
    console.error(`Error calculating smart daily premiums for ${ticker}:`, error.message);
    throw error;
  }
}

module.exports = {
  findActiveOptionsContracts,
  getTradesForContract,
  getSmartDailyPremiums
};
