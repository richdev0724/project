const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;

/**
 * Get all options contracts for a ticker with pagination
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @returns {Promise<Array>} - Array of options contract objects
 */
async function getAllOptionsContracts(ticker) {
  let allContracts = [];
  let nextUrl = null;
  let pageCount = 0;
  const maxPages = 100; // Safety limit

  let url = `https://api.polygon.io/v3/reference/options/contracts?underlying_ticker=${ticker}&limit=1000&apiKey=${POLYGON_API_KEY}`;

  while (url && pageCount < maxPages) {
    console.log(`Fetching options contracts page ${pageCount + 1}...`);
    const response = await axios.get(url);

    if (response.data.status !== 'OK') {
      console.warn(`Polygon API error fetching contracts: ${response.data.status}`);
      break;
    }

    const contracts = response.data.results || [];
    allContracts = allContracts.concat(contracts);
    nextUrl = response.data.next_url;
    url = nextUrl ? `${nextUrl}&apiKey=${POLYGON_API_KEY}` : null;
    pageCount++;

    console.log(`Page ${pageCount}: Found ${contracts.length} contracts (Total: ${allContracts.length})`);

    // Small delay to avoid rate limiting
    await new Promise(resolve => setTimeout(resolve, 100));
  }

  return allContracts;
}

/**
 * Get aggregates data for a specific options contract on a given date
 * @param {string} contractTicker - Options contract ticker (e.g., 'O:SPY251008C00500000')
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<object|null>} - Aggregates data or null if no data
 */
async function getContractAggregates(contractTicker, date) {
  try {
    const response = await axios.get(`https://api.polygon.io/v2/aggs/ticker/${contractTicker}/range/1/day/${date}/${date}`, {
      params: {
        apiKey: POLYGON_API_KEY,
        adjusted: true
      }
    });

    if (response.data.status !== 'OK' || !response.data.results || response.data.results.length === 0) {
      return null;
    }

    return response.data.results[0];
  } catch (error) {
    console.log(`No aggregates data for ${contractTicker} on ${date}:`, error.message);
    return null;
  }
}

/**
 * Get total options premiums using aggregates API
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<object>} - Total premiums data
 */
async function getTotalOptionsPremiumsFromAggregates(ticker, date) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Calculating total options premiums for ${ticker} on ${date} using aggregates...`);

    // Get all options contracts
    const allContracts = await getAllOptionsContracts(ticker);
    console.log(`Found ${allContracts.length} total options contracts for ${ticker}`);

    if (allContracts.length === 0) {
      return {
        ticker,
        date,
        totalCallPremium: 0,
        totalPutPremium: 0,
        netPremium: 0,
        totalCallVolume: 0,
        totalPutVolume: 0,
        totalContracts: 0,
        callContracts: 0,
        putContracts: 0,
        contractsWithData: 0
      };
    }

    // Process contracts in batches to avoid rate limiting
    const batchSize = 10;
    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let totalCallVolume = 0;
    let totalPutVolume = 0;
    let callContracts = 0;
    let putContracts = 0;
    let contractsWithData = 0;

    for (let i = 0; i < allContracts.length; i += batchSize) {
      const batch = allContracts.slice(i, i + batchSize);
      console.log(`Processing batch ${Math.floor(i / batchSize) + 1}/${Math.ceil(allContracts.length / batchSize)} (${batch.length} contracts)...`);

      const batchPromises = batch.map(async (contract) => {
        const aggregates = await getContractAggregates(contract.ticker, date);
        
        if (aggregates && aggregates.v > 0) { // Only process if there's volume
          contractsWithData++;
          const totalPremium = aggregates.vw * aggregates.v; // VWAP * Volume

          if (contract.contract_type === 'call') {
            totalCallPremium += totalPremium;
            totalCallVolume += aggregates.v;
            callContracts++;
          } else if (contract.contract_type === 'put') {
            totalPutPremium += totalPremium;
            totalPutVolume += aggregates.v;
            putContracts++;
          }

          console.log(`  ${contract.ticker} (${contract.contract_type}): Volume=${aggregates.v}, VWAP=${aggregates.vw}, Premium=${totalPremium.toFixed(2)}`);
        }

        return null;
      });

      await Promise.all(batchPromises);

      // Small delay between batches
      await new Promise(resolve => setTimeout(resolve, 200));
    }

    const netPremium = totalCallPremium - totalPutPremium;

    console.log(`${ticker} Total Options Premiums Summary for ${date}:`);
    console.log(`- Total Contracts: ${allContracts.length}`);
    console.log(`- Contracts with Data: ${contractsWithData}`);
    console.log(`- Call Contracts: ${callContracts}`);
    console.log(`- Put Contracts: ${putContracts}`);
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
      totalContracts: allContracts.length,
      callContracts,
      putContracts,
      contractsWithData
    };

  } catch (error) {
    console.error(`Error calculating total options premiums for ${ticker}:`, error.message);
    throw error;
  }
}

module.exports = {
  getTotalOptionsPremiumsFromAggregates,
  getAllOptionsContracts,
  getContractAggregates
};

