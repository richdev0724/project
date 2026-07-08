require('dotenv').config();
const axios = require('axios');

const API_KEY = process.env.POLYGON_API_KEY;
const BASE_URL = 'https://api.polygon.io';
const LIMIT = 250;

// In-memory cache for contract data
const contractCache = {
  data: {},
  timestamp: {},
  CACHE_DURATION: 5 * 60 * 1000 // 5 minutes
};

async function getLastPrice(ticker) {
  const url = `${BASE_URL}/v2/last/trade/${ticker}?apiKey=${API_KEY}`;

  try {
    const response = await axios.get(url);
    const price = response.data?.results?.p;

    if (price) {
      console.log(`📈 Last price of ${ticker}: $${price}`);
      return price;
    } else {
      throw new Error('Price not found in response');
    }
  } catch (err) {
    console.error('❌ Error fetching last price:', err.message);
    throw err;
  }
}

async function fetchFullChain(ticker) {
  // Check cache first
  const now = Date.now();
  if (contractCache.data[ticker] && 
      (now - contractCache.timestamp[ticker] < contractCache.CACHE_DURATION)) {
    console.log(`📦 Using cached contracts for ${ticker}`);
    return contractCache.data[ticker];
  }

  let all = [];
  let url = `${BASE_URL}/v3/snapshot/options/${ticker}?limit=${LIMIT}&apiKey=${API_KEY}`;
  let pageCount = 0;
  
  try {
    console.log(`🔄 Starting options chain fetch for ${ticker}`);
    while (url) {
      pageCount++;
      console.log(`📥 Fetching page ${pageCount} for ${ticker}`);
      const { data } = await axios.get(url);
      const results = data.results || [];
      console.log(`✅ Received ${results.length} contracts on page ${pageCount}`);
      all.push(...results);
      url = data.next_url ? `${data.next_url}&apiKey=${API_KEY}` : null;
    }
    console.log(`🎯 Completed fetching ${all.length} total contracts for ${ticker}`);
    
    // Update cache
    contractCache.data[ticker] = all;
    contractCache.timestamp[ticker] = now;
    
    return all;
  } catch (error) {
    console.error(`❌ Error fetching options chain for ${ticker}:`, error.message);
    throw error;
  }
}

function calculateGammaExposure(contracts) {
  console.log('🧮 Starting gamma exposure calculations');
  console.log(`📊 Processing ${contracts.length} contracts`);
  
  const agg = {};
  let skippedContracts = 0;
  
  for (const option of contracts) {
    const { details, greeks, open_interest: oi, day } = option;
    if (!details || !greeks || oi == null || !day || !day.volume || !day.close) {
      skippedContracts++;
      continue;
    }

    const { expiration_date: expiry, strike_price: strike, contract_type: type } = details;
    const gamma = greeks.gamma;
    const volume = day.volume;
    const price = day.close;
    const premium = price * 100 * oi; // Contract price * 100 shares * OI

    if (typeof gamma !== 'number') {
      skippedContracts++;
      continue;
    }

    const exposure = gamma * oi * 100;
    const key = `${strike}`;  // Changed to use just strike as key since we're already filtered by expiry
    
    if (!agg[key]) {
      agg[key] = { 
        strike,
        call_gamma: 0,
        put_gamma: 0,
        call_volume: 0,
        put_volume: 0,
        call_oi: 0,
        put_oi: 0,
        call_premium: 0,
        put_premium: 0
      };
    }
    
    if (type === 'call') {
      agg[key].call_gamma += exposure;
      agg[key].call_volume += volume;
      agg[key].call_oi += oi;
      agg[key].call_premium += premium;
    } else if (type === 'put') {
      agg[key].put_gamma += exposure;
      agg[key].put_volume += volume;
      agg[key].put_oi += oi;
      agg[key].put_premium += premium;
    }
  }

  const results = Object.values(agg).map(v => ({
    strike_price: v.strike,
    call_gamma: Number(v.call_gamma.toFixed(0)),
    put_gamma: Number((-v.put_gamma).toFixed(0)),   // negative for put gamma
    net_gamma: Number((v.call_gamma + (-v.put_gamma)).toFixed(0)),  // Combined net gamma
    positive_gex: v.call_gamma > 0 ? Number(v.call_gamma.toFixed(0)) : 0,
    negative_gex: v.put_gamma < 0 ? Number(v.put_gamma.toFixed(0)) : 0,
    // Add volume, OI, and premium data
    call_volume: v.call_volume,
    put_volume: v.put_volume,
    net_volume: v.call_volume - v.put_volume,
    call_oi: v.call_oi,
    put_oi: v.put_oi,
    net_oi: v.call_oi - v.put_oi,
    call_premium: Number(v.call_premium.toFixed(0)),
    put_premium: Number(v.put_premium.toFixed(0)),
    net_premium: Number((v.call_premium - v.put_premium).toFixed(0))
  }));

  console.log(`📈 Gamma exposure calculated for ${results.length} strike prices`);
  console.log(`⚠️ Skipped ${skippedContracts} contracts due to missing data`);
  
  return results;
}

async function getExpirationDates(ticker) {
  try {
    console.log(`🔍 Fetching expiration dates for ${ticker}`);
    const chain = await fetchFullChain(ticker);
    console.log(`📋 Processing ${chain.length} contracts for expiration dates`);
    
    const dates = new Set();
    let invalidCount = 0;
    
    chain.forEach(option => {
      if (option.details && option.details.expiration_date) {
        dates.add(option.details.expiration_date);
      } else {
        invalidCount++;
      }
    });

    const sortedDates = Array.from(dates).sort();
    console.log(`📅 Found ${sortedDates.length} unique expiration dates for ${ticker}`);
    console.log(`⚠️ Skipped ${invalidCount} contracts with missing expiration dates`);
    console.log('Available expiration dates:', sortedDates);
    
    // Group contracts by expiration to show distribution
    const contractsByExpiry = {};
    chain.forEach(option => {
      if (option.details && option.details.expiration_date) {
        const date = option.details.expiration_date;
        contractsByExpiry[date] = (contractsByExpiry[date] || 0) + 1;
      }
    });
    
    console.log('Contracts per expiration:');
    Object.entries(contractsByExpiry)
      .sort(([a], [b]) => a.localeCompare(b))
      .forEach(([date, count]) => {
        console.log(`  ${date}: ${count} contracts`);
      });

    return sortedDates;
  } catch (error) {
    console.error(`❌ Error getting expiration dates for ${ticker}:`, error.message);
    throw error;
  }
}

async function getGammaExposure(ticker, expiration) {
  if (!expiration) {
    throw new Error('Expiration date is required for gamma exposure calculation');
  }

  try {
    console.log(`🎯 Starting gamma exposure calculation for ${ticker} at expiration ${expiration}`);
    const chain = await fetchFullChain(ticker);
    const lastPrice = await getLastPrice(ticker);
    
    console.log(`🔍 Filtering chain for expiration date: ${expiration}`);
    const filteredChain = chain.filter(option => 
      option.details && option.details.expiration_date === expiration
    );
    console.log(`📊 Found ${filteredChain.length} contracts for expiration ${expiration}`);

    const exposureData = calculateGammaExposure(filteredChain);
    const sortedData = exposureData.sort((a, b) => a.strike_price - b.strike_price);
    console.log(`✅ Gamma exposure calculation completed for ${ticker}`);
    return { data: sortedData, lastPrice };
  } catch (error) {
    console.error(`❌ Error calculating gamma exposure for ${ticker}:`, error.message);
    throw error;
  }
}

module.exports = {
  getGammaExposure,
  getExpirationDates
}; 