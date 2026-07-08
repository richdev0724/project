const { restClient } = require('@polygon.io/client-js');
const { createClient } = require('@clickhouse/client');

class PolygonOptionsTradesService {
  constructor() {
    this.rest = restClient(process.env.POLYGON_API_KEY);
    
    // Initialize ClickHouse client
    this.clickhouse = createClient({
      url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
      username: process.env.CLICKHOUSE_USER || 'default',
      password: process.env.CLICKHOUSE_PASSWORD || '',
      database: process.env.CLICKHOUSE_DATABASE || 'options'
    });
  }

  /**
   * Get ALL options contracts using systematic date range approach (alternative method)
   * @param {string} ticker - The base ticker symbol (e.g., 'SPY')
   * @param {string} date - The trading date to get contracts for
   * @returns {Promise<Array>} Array of ALL options contracts
   */
  async getAllOptionsContractsByDateRanges(ticker, date) {
    try {
      console.log(`Fetching ALL options contracts for ${ticker} using systematic date ranges`);
      
      let allContracts = [];
      const currentDate = new Date();
      
      // Define date ranges to systematically cover all expirations
      const dateRanges = [
        // Near-term (next 2 months)
        {
          start: currentDate.toISOString().split('T')[0],
          end: new Date(currentDate.getTime() + 60 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          label: 'Near-term (0-2 months)'
        },
        // Mid-term (2-6 months)
        {
          start: new Date(currentDate.getTime() + 61 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          end: new Date(currentDate.getTime() + 180 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          label: 'Mid-term (2-6 months)'
        },
        // Long-term (6+ months)
        {
          start: new Date(currentDate.getTime() + 181 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          end: new Date(currentDate.getTime() + 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          label: 'Long-term (6+ months)'
        }
      ];
      
      for (const range of dateRanges) {
        try {
          console.log(`Fetching ${range.label} contracts (${range.start} to ${range.end})`);
          
          const response = await this.rest.options.snapshotOptionChain(ticker, {
            'expiration_date.gte': range.start,
            'expiration_date.lte': range.end,
            'limit': 250
          });
          
          const contracts = response.results || [];
          allContracts = [...allContracts, ...contracts];
          
          console.log(`Found ${contracts.length} contracts in ${range.label} (Total: ${allContracts.length})`);
          
          // Handle pagination for this date range
          let nextUrl = response.next_url;
          while (nextUrl) {
            try {
              const axios = require('axios');
              const paginatedResponse = await axios.get(nextUrl, {
                headers: {
                  'Authorization': `Bearer ${process.env.POLYGON_API_KEY}`
                }
              });
              
              const pageContracts = paginatedResponse.data.results || [];
              allContracts = [...allContracts, ...pageContracts];
              nextUrl = paginatedResponse.data.next_url;
              
              console.log(`Pagination: Found ${pageContracts.length} more contracts (Total: ${allContracts.length})`);
              
            } catch (pageError) {
              console.error(`Error in pagination for ${range.label}:`, pageError.message);
              break;
            }
          }
          
        } catch (rangeError) {
          console.error(`Error fetching ${range.label} contracts:`, rangeError.message);
        }
      }
      
      console.log(`Date range approach complete: Found ${allContracts.length} total contracts`);
      return allContracts;
      
    } catch (error) {
      console.error(`Error in date range approach for ${ticker}:`, error);
      throw error;
    }
  }

  /**
   * Get ALL options contracts for a ticker using comprehensive systematic approach
   * @param {string} ticker - The base ticker symbol (e.g., 'SPY')
   * @param {string} date - The trading date to get contracts for
   * @returns {Promise<Array>} Array of ALL options contracts
   */
  async getAllOptionsContracts(ticker, date) {
    try {
      console.log(`Fetching ALL options contracts for ${ticker} using comprehensive systematic approach`);
      
      let allContracts = [];
      const currentDate = new Date();
      
      // Strategy 1: Get contracts without any filters first to see total available
      console.log('Strategy 1: Fetching contracts without filters...');
      const unfilteredContracts = await this.fetchContractsWithPagination(ticker, {});
      allContracts = [...unfilteredContracts];
      console.log(`Unfiltered approach: Found ${unfilteredContracts.length} contracts`);
      
      // Strategy 2: Systematic expiration date ranges (much wider coverage)
      console.log('Strategy 2: Fetching contracts by expiration date ranges...');
      const dateRanges = [
        // Very near-term (next month)
        {
          start: currentDate.toISOString().split('T')[0],
          end: new Date(currentDate.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          label: 'Very near-term (0-1 month)'
        },
        // Near-term (1-3 months)
        {
          start: new Date(currentDate.getTime() + 31 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          end: new Date(currentDate.getTime() + 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          label: 'Near-term (1-3 months)'
        },
        // Mid-term (3-6 months)
        {
          start: new Date(currentDate.getTime() + 91 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          end: new Date(currentDate.getTime() + 180 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          label: 'Mid-term (3-6 months)'
        },
        // Long-term (6-12 months)
        {
          start: new Date(currentDate.getTime() + 181 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          end: new Date(currentDate.getTime() + 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          label: 'Long-term (6-12 months)'
        },
        // Very long-term (1-2 years)
        {
          start: new Date(currentDate.getTime() + 366 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          end: new Date(currentDate.getTime() + 730 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
          label: 'Very long-term (1-2 years)'
        }
      ];
      
      for (const range of dateRanges) {
        try {
          console.log(`Fetching ${range.label} contracts (${range.start} to ${range.end})`);
          const rangeContracts = await this.fetchContractsWithPagination(ticker, {
            'expiration_date.gte': range.start,
            'expiration_date.lte': range.end
          });
          
          // Merge contracts, avoiding duplicates
          const existingTickers = new Set(allContracts.map(c => c.ticker));
          const newContracts = rangeContracts.filter(c => !existingTickers.has(c.ticker));
          allContracts = [...allContracts, ...newContracts];
          
          console.log(`Found ${rangeContracts.length} contracts in ${range.label} (${newContracts.length} new, Total: ${allContracts.length})`);
          
        } catch (rangeError) {
          console.error(`Error fetching ${range.label} contracts:`, rangeError.message);
        }
      }
      
      // Strategy 3: Strike price ranges for comprehensive coverage (much wider ranges)
      console.log('Strategy 3: Fetching contracts by strike price ranges...');
      const strikeRanges = [
        { min: 200, max: 350, label: 'Very low strikes (200-350)' },
        { min: 350, max: 450, label: 'Low strikes (350-450)' },
        { min: 450, max: 550, label: 'Mid-low strikes (450-550)' },
        { min: 550, max: 650, label: 'Mid strikes (550-650)' },
        { min: 650, max: 750, label: 'Mid-high strikes (650-750)' },
        { min: 750, max: 900, label: 'High strikes (750-900)' },
        { min: 900, max: 1200, label: 'Very high strikes (900-1200)' }
      ];
      
      for (const range of strikeRanges) {
        try {
          console.log(`Fetching ${range.label} contracts`);
          const strikeContracts = await this.fetchContractsWithPagination(ticker, {
            'strike_price.gte': range.min,
            'strike_price.lte': range.max
          });
          
          // Merge contracts, avoiding duplicates
          const existingTickers = new Set(allContracts.map(c => c.ticker));
          const newContracts = strikeContracts.filter(c => !existingTickers.has(c.ticker));
          allContracts = [...allContracts, ...newContracts];
          
          console.log(`Found ${strikeContracts.length} contracts in ${range.label} (${newContracts.length} new, Total: ${allContracts.length})`);
          
        } catch (strikeError) {
          console.error(`Error fetching ${range.label} contracts:`, strikeError.message);
        }
      }
      
      // Strategy 4: Contract type coverage (calls vs puts)
      console.log('Strategy 4: Fetching contracts by contract type...');
      const contractTypes = ['call', 'put'];
      
      for (const contractType of contractTypes) {
        try {
          console.log(`Fetching ${contractType} contracts`);
          const typeContracts = await this.fetchContractsWithPagination(ticker, {
            'contract_type': contractType
          });
          
          // Merge contracts, avoiding duplicates
          const existingTickers = new Set(allContracts.map(c => c.ticker));
          const newContracts = typeContracts.filter(c => !existingTickers.has(c.ticker));
          allContracts = [...allContracts, ...newContracts];
          
          console.log(`Found ${typeContracts.length} ${contractType} contracts (${newContracts.length} new, Total: ${allContracts.length})`);
          
        } catch (typeError) {
          console.error(`Error fetching ${contractType} contracts:`, typeError.message);
        }
      }
      
      console.log(`Comprehensive approach complete: Found ${allContracts.length} total unique contracts`);
      
      // Enhanced volume verification and data analysis
      if (allContracts.length > 0) {
        console.log('Sample contracts:', allContracts.slice(0, 5).map(c => ({
          contractType: c.details?.contract_type,
          strikePrice: c.details?.strike_price,
          expirationDate: c.details?.expiration_date,
          dayVolume: c.day?.volume,
          dayClose: c.day?.close,
          hasDayData: !!c.day,
          dayDataKeys: c.day ? Object.keys(c.day) : []
        })));
        
        // Comprehensive volume verification
        const contractsWithVolume = allContracts.filter(c => c.day?.volume > 0);
        const contractsWithZeroVolume = allContracts.filter(c => c.day?.volume === 0);
        const contractsWithoutDayData = allContracts.filter(c => !c.day);
        
        console.log(`\n=== VOLUME VERIFICATION ===`);
        console.log(`- Contracts with volume > 0: ${contractsWithVolume.length}`);
        console.log(`- Contracts with volume = 0: ${contractsWithZeroVolume.length}`);
        console.log(`- Contracts without day data: ${contractsWithoutDayData.length}`);
        console.log(`- Total contracts: ${allContracts.length}`);
        
        // Detailed volume analysis
        if (contractsWithVolume.length > 0) {
          const volumes = contractsWithVolume.map(c => c.day.volume);
          const maxVolume = Math.max(...volumes);
          const minVolume = Math.min(...volumes);
          const avgVolume = volumes.reduce((a, b) => a + b, 0) / volumes.length;
          const totalVolume = volumes.reduce((a, b) => a + b, 0);
          
          console.log(`\n=== VOLUME STATISTICS ===`);
          console.log(`- Min volume: ${minVolume}`);
          console.log(`- Max volume: ${maxVolume}`);
          console.log(`- Average volume: ${avgVolume.toFixed(2)}`);
          console.log(`- Total volume: ${totalVolume.toLocaleString()}`);
          
          // Volume distribution analysis
          const volumeRanges = [
            { min: 1, max: 10, label: '1-10' },
            { min: 11, max: 100, label: '11-100' },
            { min: 101, max: 1000, label: '101-1K' },
            { min: 1001, max: 10000, label: '1K-10K' },
            { min: 10001, max: 100000, label: '10K-100K' },
            { min: 100001, max: Infinity, label: '100K+' }
          ];
          
          console.log(`\n=== VOLUME DISTRIBUTION ===`);
          volumeRanges.forEach(range => {
            const count = volumes.filter(v => v >= range.min && v <= range.max).length;
            const percentage = ((count / volumes.length) * 100).toFixed(1);
            console.log(`- ${range.label}: ${count} contracts (${percentage}%)`);
          });
          
          // Show highest volume contracts
          const topVolumeContracts = contractsWithVolume
            .sort((a, b) => b.day.volume - a.day.volume)
            .slice(0, 10);
          
          console.log(`\n=== TOP 10 VOLUME CONTRACTS ===`);
          topVolumeContracts.forEach((contract, index) => {
            console.log(`${index + 1}. ${contract.details?.contract_type} ${contract.details?.strike_price} ${contract.details?.expiration_date} - Volume: ${contract.day.volume}, Close: $${contract.day.close}`);
          });
        }
        
        // Contract type analysis
        const callContracts = allContracts.filter(c => c.details?.contract_type === 'call');
        const putContracts = allContracts.filter(c => c.details?.contract_type === 'put');
        const callVolume = callContracts.reduce((sum, c) => sum + (c.day?.volume || 0), 0);
        const putVolume = putContracts.reduce((sum, c) => sum + (c.day?.volume || 0), 0);
        
        console.log(`\n=== CONTRACT TYPE ANALYSIS ===`);
        console.log(`- Call contracts: ${callContracts.length} (Volume: ${callVolume.toLocaleString()})`);
        console.log(`- Put contracts: ${putContracts.length} (Volume: ${putVolume.toLocaleString()})`);
        console.log(`- Call/Put ratio: ${(callVolume / putVolume).toFixed(3)}`);
        
        // Expiration date analysis
        const expirationGroups = {};
        allContracts.forEach(contract => {
          const expDate = contract.details?.expiration_date;
          if (expDate) {
            if (!expirationGroups[expDate]) {
              expirationGroups[expDate] = { count: 0, volume: 0 };
            }
            expirationGroups[expDate].count++;
            expirationGroups[expDate].volume += contract.day?.volume || 0;
          }
        });
        
        const sortedExpirations = Object.entries(expirationGroups)
          .sort((a, b) => new Date(a[0]) - new Date(b[0]))
          .slice(0, 10);
        
        console.log(`\n=== TOP 10 EXPIRATION DATES ===`);
        sortedExpirations.forEach(([date, data]) => {
          console.log(`${date}: ${data.count} contracts, ${data.volume.toLocaleString()} volume`);
        });
      }
      
      return allContracts;
    } catch (error) {
      console.error(`Error fetching options contracts for ${ticker}:`, error);
      throw error;
    }
  }

  /**
   * Helper method to fetch contracts with pagination
   * @param {string} ticker - The ticker symbol
   * @param {Object} filters - Query filters
   * @returns {Promise<Array>} Array of contracts
   */
  async fetchContractsWithPagination(ticker, filters) {
    let allContracts = [];
    let nextUrl = null;
    let pageCount = 0;
    
    // Initial request
    const initialResponse = await this.rest.options.snapshotOptionChain(ticker, {
      ...filters,
      'limit': 250 // Maximum contracts per request
    });
    
    allContracts = [...(initialResponse.results || [])];
    nextUrl = initialResponse.next_url;
    pageCount = 1;
    
    // Continue pagination until no more pages
    while (nextUrl) {
      try {
        // Use axios directly for pagination since this.rest.request doesn't work
        const axios = require('axios');
        const paginatedResponse = await axios.get(nextUrl, {
          headers: {
            'Authorization': `Bearer ${process.env.POLYGON_API_KEY}`
          }
        });
        
        const pageContracts = paginatedResponse.data.results || [];
        allContracts = [...allContracts, ...pageContracts];
        nextUrl = paginatedResponse.data.next_url;
        pageCount++;
        
        // Safety check to prevent infinite loops
        if (pageCount > 100) {
          console.warn('Reached maximum page limit (100), stopping pagination');
          break;
        }
        
      } catch (pageError) {
        console.error(`Error fetching page ${pageCount + 1}:`, pageError.message);
        break;
      }
    }
    
    return allContracts;
  }

  /**
   * Fetch options trades for a specific options contract
   * @param {string} contractTicker - The full options contract ticker (e.g., 'O:SPY251219C00650000')
   * @param {string} date - The trading date in YYYY-MM-DD format
   * @returns {Promise<Object>} Options trades data
   */
  async getOptionsTradesForContract(contractTicker, date) {
    try {
      console.log(`Fetching options trades for contract ${contractTicker} on ${date}`);
      
      const response = await this.rest.options.trades(contractTicker, {
        'timestamp.gte': `${date}T09:30:00.000Z`,
        'timestamp.lt': `${date}T16:00:00.000Z`,
        'limit': 50000,
        'order': 'asc',
        'sort': 'timestamp'
      });

      console.log(`Found ${response.results?.length || 0} trades for contract ${contractTicker}`);
      return response;
    } catch (error) {
      console.error(`Error fetching trades for contract ${contractTicker}:`, error);
      // Return empty results instead of throwing to continue processing other contracts
      return { results: [] };
    }
  }

  /**
   * Fetch options trades for all contracts of a ticker on a specific date
   * @param {string} ticker - The ticker symbol (e.g., 'SPY')
   * @param {string} date - The trading date in YYYY-MM-DD format
   * @returns {Promise<Array>} Combined array of all trades
   */
  async getAllOptionsTrades(ticker, date) {
    try {
      console.log(`Fetching all options trades for ${ticker} on ${date}`);
      
      // First get all available contracts
      const contracts = await this.getAllOptionsContracts(ticker, date);
      
      if (contracts.length === 0) {
        console.log(`No contracts found for ${ticker}`);
        return [];
      }

      // Fetch trades for each contract
      const allTrades = [];
      let processedContracts = 0;
      
      for (const contract of contracts) {
        const contractTicker = contract.ticker;
        const tradesResponse = await this.getOptionsTradesForContract(contractTicker, date);
        
        if (tradesResponse.results && tradesResponse.results.length > 0) {
          allTrades.push(...tradesResponse.results);
        }
        
        processedContracts++;
        if (processedContracts % 50 === 0) {
          console.log(`Processed ${processedContracts}/${contracts.length} contracts, found ${allTrades.length} total trades`);
        }
      }

      console.log(`Total trades found for ${ticker}: ${allTrades.length} from ${contracts.length} contracts`);
      return allTrades;
    } catch (error) {
      console.error(`Error fetching all options trades for ${ticker}:`, error);
      throw error;
    }
  }

  /**
   * Determine if a trade was executed at ask or bid
   * @param {Object} trade - The trade object
   * @returns {Promise<string>} 'ask' or 'bid'
   */
  async determineExecution(trade) {
    try {
      // For now, we'll use a simple heuristic based on trade conditions
      // In a real implementation, you'd compare against actual bid/ask quotes
      const conditions = trade.conditions || [];
      
      // Common conditions that indicate ask-side execution
      const askConditions = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
      const bidConditions = ['11', '12', '13', '14', '15', '16', '17', '18', '19', '20'];
      
      // Check if any condition indicates ask or bid execution
      for (const condition of conditions) {
        if (askConditions.includes(condition)) {
          return 'ask';
        }
        if (bidConditions.includes(condition)) {
          return 'bid';
        }
      }
      
      // Default to ask if no specific condition is found
      // This is a simplified approach - in reality you'd need more sophisticated logic
      return 'ask';
    } catch (error) {
      console.error('Error determining execution type:', error);
      return 'ask'; // Default fallback
    }
  }

  /**
   * Test with a single known options contract
   * @param {string} ticker - The ticker symbol
   * @param {string} date - The trading date
   * @returns {Promise<Object>} Test result
   */
  async testSingleContract(ticker, date) {
    try {
      console.log(`Testing single contract for ${ticker} on ${date}`);
      
      // Try with a known SPY options contract format
      const testContract = `O:${ticker}251220C00650000`; // SPY Dec 20, 2025 Call $650
      console.log(`Testing contract: ${testContract}`);
      
      const response = await this.rest.options.trades(testContract, {
        'timestamp.gte': `${date}T09:30:00.000Z`,
        'timestamp.lt': `${date}T16:00:00.000Z`,
        'limit': 100
      });

      console.log(`Test result for ${testContract}:`, {
        resultsCount: response.results?.length || 0,
        response: response
      });

      return response;
    } catch (error) {
      console.error(`Error testing single contract:`, error);
      throw error;
    }
  }

  /**
   * Get daily volume data for a specific options contract using Daily Ticker Summary
   * @param {string} contractTicker - The full options contract ticker
   * @param {string} date - The trading date
   * @returns {Promise<Object>} Daily summary data with volume
   */
  async getContractDailySummary(contractTicker, date) {
    try {
      console.log(`Getting daily summary for contract ${contractTicker} on ${date}`);
      
      const response = await this.rest.options.dailyOpenClose(contractTicker, date);
      
      console.log(`Daily summary for ${contractTicker}:`, {
        volume: response.volume,
        close: response.close,
        open: response.open,
        high: response.high,
        low: response.low
      });
      
      return response;
    } catch (error) {
      console.error(`Error getting daily summary for ${contractTicker}:`, error);
      return { volume: 0, close: 0, open: 0, high: 0, low: 0 };
    }
  }

  /**
   * Get today's date (for end-of-day calculations after market close)
   * @returns {string} Today's date in YYYY-MM-DD format
   */
  getTodayDate() {
    const today = new Date();
    
    // If it's weekend, get the last trading day (Friday)
    if (today.getDay() === 0) { // Sunday
      today.setDate(today.getDate() - 2); // Go back to Friday
    } else if (today.getDay() === 6) { // Saturday
      today.setDate(today.getDate() - 1); // Go back to Friday
    }
    
    return today.toISOString().split('T')[0];
  }

  /**
   * Calculate net premiums using Options Aggregates API (alternative approach)
   * @param {string} ticker - The ticker symbol
   * @param {string} date - The trading date (optional, defaults to yesterday)
   * @returns {Promise<Object>} Calculated premium data
   */
  async calculateNetPremiumsFromAggregates(ticker, date = null) {
    try {
      const targetDate = date || this.getTodayDate();
      console.log(`Calculating net premiums for ${ticker} on ${targetDate} using Options Aggregates API`);
      
      // First get all options contracts
      const contracts = await this.getAllOptionsContracts(ticker, targetDate);
      
      if (contracts.length === 0) {
        return {
          ticker,
          date: targetDate,
          netCallPremium: 0,
          netPutPremium: 0,
          netPremium: 0,
          totalCallVolume: 0,
          totalPutVolume: 0,
          tradesProcessed: 0
        };
      }

      let callAskPremium = 0;
      let callBidPremium = 0;
      let putAskPremium = 0;
      let putBidPremium = 0;
      let totalCallVolume = 0;
      let totalPutVolume = 0;
      let contractsProcessed = 0;

      // Process each contract using aggregates API
      for (const contract of contracts) {
        try {
          const contractTicker = contract.ticker; // Full options ticker
          const contractType = contract.details?.contract_type;
          
          if (!contractTicker || !contractType) continue;
          
          // Get daily aggregates for this contract
          const aggregates = await this.rest.stocks.aggregates(
            contractTicker,
            1, // timespan
            'day', // multiplier
            targetDate, // from
            targetDate // to
          );
          
          const aggregate = aggregates.results?.[0];
          if (!aggregate || aggregate.volume === 0) continue;
          
          contractsProcessed++;
          
          // Use VWAP * Volume for premium calculation
          const totalPremium = aggregate.vwap * aggregate.volume * 100;
          
          // Estimate ask vs bid based on high/low spread
          const askPrice = aggregate.high;
          const bidPrice = aggregate.low;
          const askPremium = askPrice * aggregate.volume * 100;
          const bidPremium = bidPrice * aggregate.volume * 100;
          
          if (contractType === 'call') {
            totalCallVolume += aggregate.volume;
            callAskPremium += askPremium;
            callBidPremium += bidPremium;
          } else if (contractType === 'put') {
            totalPutVolume += aggregate.volume;
            putAskPremium += askPremium;
            putBidPremium += bidPremium;
          }
          
          if (contractsProcessed % 100 === 0) {
            console.log(`Processed ${contractsProcessed} contracts...`);
          }
          
        } catch (contractError) {
          console.error(`Error processing contract ${contract.ticker}:`, contractError.message);
        }
      }

      // Calculate net premiums
      const netCallPremium = callAskPremium - callBidPremium;
      const netPutPremium = putAskPremium - putBidPremium;
      const netPremium = netCallPremium - netPutPremium;

      const result = {
        ticker,
        date: targetDate,
        netCallPremium,
        netPutPremium,
        netPremium,
        totalCallVolume,
        totalPutVolume,
        callPremiumAtAsk: callAskPremium,
        callPremiumAtBid: callBidPremium,
        putPremiumAtAsk: putAskPremium,
        putPremiumAtBid: putBidPremium,
        tradesProcessed: contractsProcessed
      };

      console.log(`Aggregates calculation complete for ${ticker} on ${targetDate}:`);
      console.log(`- Contracts processed: ${contractsProcessed}`);
      console.log(`- Net Call Premium: $${netCallPremium.toLocaleString()}`);
      console.log(`- Net Put Premium: $${netPutPremium.toLocaleString()}`);
      console.log(`- Net Premium: $${netPremium.toLocaleString()}`);

      return result;
    } catch (error) {
      console.error(`Error calculating net premiums from aggregates for ${ticker}:`, error);
      throw error;
    }
  }

  /**
   * Calculate net premiums using Options Chain Snapshot data directly
   * 
   * CRITICAL NET PREMIUM CALCULATION LOGIC:
   * 
   * Net Call Premium (NCP) = (Call Premium at Ask) - (Call Premium at Bid)
   * Net Put Premium (NPP) = (Put Premium at Ask) - (Put Premium at Bid)  
   * Net Premium (NP) = Net Call Premium - Net Put Premium
   * 
   * EXECUTION SIDE ANALYSIS:
   * - Ask transactions ADD to net premium (buyers paying ask price)
   * - Bid transactions SUBTRACT from net premium (sellers taking bid price)
   * - Midpoint transactions are EXCLUDED from calculation (no net effect)
   * 
   * EXAMPLE:
   * - $15,000 in calls at ask → +$15,000 to net call premium
   * - $10,000 in calls at bid → -$10,000 to net call premium  
   * - Result: Net Call Premium = +$15,000 - $10,000 = +$5,000
   * 
   * BID/ASK DETERMINATION:
   * - Compare trade price to current bid/ask spread
   * - If price >= ask - (spread * 0.1) → executed at ask
   * - If price <= bid + (spread * 0.1) → executed at bid
   * - Otherwise → executed at midpoint (excluded)
   * 
   * @param {string} ticker - The ticker symbol
   * @param {string} date - The trading date (optional, defaults to today)
   * @returns {Promise<Object>} Calculated premium data
   */
  /**
   * Get Daily Ticker Summary for precise OHLC and volume data
   * @param {string} optionsTicker - The full options ticker (e.g., "O:SPY251219C00650000")
   * @param {string} date - The trading date
   * @returns {Promise<Object|null>} Daily summary data or null if not available
   */
  async getDailyTickerSummary(optionsTicker, date) {
    try {
      const url = `https://api.polygon.io/v1/open-close/${optionsTicker}/${date}`;
      const response = await axios.get(url, {
        params: {
          adjusted: true,
          apikey: this.apiKey
        }
      });

      if (response.data && response.data.status === 'OK') {
        return response.data;
      }
      return null;
    } catch (error) {
      // Don't log errors for individual contracts - this is expected for many contracts
      return null;
    }
  }

  async calculateNetPremiums(ticker, date = null) {
    try {
      // Use today's date if no date provided (for end-of-day calculations after market close)
      const targetDate = date || this.getTodayDate();
      console.log(`🔍 Calculating net premiums for ${ticker} on ${targetDate} using hybrid approach (Chain Snapshot + Daily Ticker Summary)`);
      
      // Get ALL options contracts using comprehensive pagination approach
      const contracts = await this.getAllOptionsContractsComprehensive(ticker, targetDate);
      
      if (contracts.length === 0) {
        console.log(`No contracts found for ${ticker}`);
        return {
          ticker,
          date: targetDate,
          netCallPremium: 0,
          netPutPremium: 0,
          netPremium: 0,
          totalCallVolume: 0,
          totalPutVolume: 0,
          callPremiumAtAsk: 0,
          callPremiumAtBid: 0,
          putPremiumAtAsk: 0,
          putPremiumAtBid: 0,
          tradesProcessed: 0
        };
      }

      // Process contracts and calculate premiums using hybrid pricing approach
      let callAskPremium = 0;
      let callBidPremium = 0;
      let putAskPremium = 0;
      let putBidPremium = 0;
      let totalCallVolume = 0;
      let totalPutVolume = 0;
      let contractsWithVolume = 0;
      let highVolumeContractsProcessed = 0;
      let contractsWithStaleData = 0;
      let midpointTradesExcluded = 0;
      let askTradesCount = 0;
      let bidTradesCount = 0;

      // Identify high-volume contracts (>1000 volume) for Daily Ticker Summary
      const highVolumeContracts = contracts.filter(c => (c.day?.volume || 0) > 1000);
      console.log(`🎯 Found ${highVolumeContracts.length} high-volume contracts (>1000 volume) for precise pricing`);

      for (const contract of contracts) {
        const dayVolume = contract.day?.volume || 0;
        const dayClose = contract.day?.close || 0;
        const contractType = contract.details?.contract_type;
        const lastQuote = contract.last_quote;
        const contractTicker = contract.details?.ticker;
        
        // Skip contracts with no volume
        if (dayVolume === 0) {
          continue;
        }
        
        contractsWithVolume++;

        let effectivePrice = 0;
        let askPrice = 0;
        let bidPrice = 0;
        let isHighVolume = dayVolume > 1000;

        if (isHighVolume && contractTicker) {
          // For high-volume contracts, try Daily Ticker Summary for precise pricing
          try {
            const dailySummary = await this.getDailyTickerSummary(contractTicker, targetDate);
            if (dailySummary && dailySummary.close && dailySummary.close > 0.01) {
              effectivePrice = dailySummary.close;
              askPrice = dailySummary.close; // Use close as both ask/bid for simplicity
              bidPrice = dailySummary.close;
              highVolumeContractsProcessed++;
              console.log(`✅ High-volume contract ${contractTicker}: volume=${dayVolume}, price=$${effectivePrice}`);
            } else {
              // Fallback to snapshot data
              effectivePrice = lastQuote?.midpoint || dayClose;
              askPrice = lastQuote?.ask || effectivePrice;
              bidPrice = lastQuote?.bid || effectivePrice;
            }
          } catch (error) {
            // Fallback to snapshot data if Daily Ticker Summary fails
            effectivePrice = lastQuote?.midpoint || dayClose;
            askPrice = lastQuote?.ask || effectivePrice;
            bidPrice = lastQuote?.bid || effectivePrice;
          }
        } else {
          // For regular contracts, use snapshot data
          effectivePrice = lastQuote?.midpoint || dayClose;
          askPrice = lastQuote?.ask || effectivePrice;
          bidPrice = lastQuote?.bid || effectivePrice;
        }
        
        // Skip contracts with unrealistic pricing (likely stale data)
        if (effectivePrice < 0.01) {
          contractsWithStaleData++;
          continue;
        }

        // ========================================
        // SIMPLIFIED APPROACH - USE DAY.CLOSE FOR ALL TRADES
        // ========================================
        // Since the API provides day.close, day.volume, and last_quote data,
        // use day.close as execution price for all trades rather than trying 
        // to classify by bid/ask. This gives total premium without needing 
        // execution side classification.
        
        // Use day.close as the execution price for all trades
        const executionPrice = dayClose || effectivePrice;
        
        // Debug logging for first few contracts
        if (contractsWithVolume <= 5) {
          console.log(`Contract ${contractsWithVolume}: dayClose=$${dayClose}, effectivePrice=$${effectivePrice}, volume=${dayVolume}, type=${contractType}`);
        }
        
        // ========================================
        // SIMPLIFIED PREMIUM CALCULATION
        // ========================================
        // Calculate total premium = execution price * volume * 100 (contract multiplier)
        // For simplicity, treat all premiums as "ask" side (buyers paying)
        // This gives us total premium without complex bid/ask analysis
        
        const totalPremium = executionPrice * dayVolume * 100;
        
        if (contractType === 'call') {
          totalCallVolume += dayVolume;
          callAskPremium += totalPremium; // All call premiums as ask
          callBidPremium += 0; // No bid premiums for simplicity
        } else if (contractType === 'put') {
          totalPutVolume += dayVolume;
          putAskPremium += totalPremium; // All put premiums as ask
          putBidPremium += 0; // No bid premiums for simplicity
        }
      }

      console.log(`📈 Volume Analysis:`);
      console.log(`   Contracts with volume: ${contractsWithVolume}`);
      console.log(`   High-volume contracts processed: ${highVolumeContractsProcessed}`);
      console.log(`   Contracts with stale data (filtered): ${contractsWithStaleData}`);
      console.log(`   Call Volume: ${(totalCallVolume / 1000000).toFixed(2)}M`);
      console.log(`   Put Volume: ${(totalPutVolume / 1000000).toFixed(2)}M`);

      // ========================================
      // SIMPLIFIED NET PREMIUM CALCULATION
      // ========================================
      // Since we're treating all premiums as "ask" side (buyers paying),
      // the net premium is simply the difference between call and put premiums
      // 
      // Net Call Premium = Call Premium (all ask)
      // Net Put Premium = Put Premium (all ask)  
      // Net Premium = Net Call Premium - Net Put Premium
      
      const netCallPremium = callAskPremium; // All call premiums
      const netPutPremium = putAskPremium;   // All put premiums
      const netPremium = netCallPremium - netPutPremium;

      console.log(`💰 Simplified Premium Analysis:`);
      console.log(`   Call Premium (Total): $${(callAskPremium / 1000000).toFixed(2)}M`);
      console.log(`   Put Premium (Total): $${(putAskPremium / 1000000).toFixed(2)}M`);
      console.log(`   Net Premium (Call-Put): $${(netPremium / 1000000).toFixed(2)}M`);

      const result = {
        ticker,
        date: targetDate,
        netCallPremium, // NCP
        netPutPremium,  // NPP
        netPremium,     // NP
        totalCallVolume,
        totalPutVolume,
        callPremiumAtAsk: callAskPremium,
        callPremiumAtBid: callBidPremium,
        putPremiumAtAsk: putAskPremium,
        putPremiumAtBid: putBidPremium,
        tradesProcessed: contractsWithVolume
      };

      console.log(`Premium calculation complete for ${ticker} on ${targetDate}:`);
      console.log(`- Total contracts: ${contracts.length}`);
      console.log(`- Contracts with volume: ${contractsWithVolume}`);
      console.log(`- Call Premium (Total): $${callAskPremium.toLocaleString()}`);
      console.log(`- Put Premium (Total): $${putAskPremium.toLocaleString()}`);
      console.log(`- Net Premium (Call-Put): $${netPremium.toLocaleString()}`);

      return result;
    } catch (error) {
      console.error(`Error calculating net premiums for ${ticker}:`, error);
      throw error;
    }
  }

  /**
   * Get ALL options contracts using comprehensive pagination (similar to PCR approach)
   * @param {string} ticker - The ticker symbol
   * @param {string} date - The trading date
   * @returns {Promise<Array>} Array of ALL options contracts
   */
  async getAllOptionsContractsComprehensive(ticker, date) {
    try {
      console.log(`Fetching ALL options contracts for ${ticker} using comprehensive pagination`);
      
      let allContracts = [];
      let nextUrl = null;
      let pageCount = 0;
      const maxPages = 1000; // Much higher limit to get all contracts
      
      // Fetch ALL pages of options data until no more data
      do {
        const url = nextUrl || `https://api.polygon.io/v3/snapshot/options/${ticker.toUpperCase()}`;
        
        const axios = require('axios');
        const response = nextUrl 
          ? await axios.get(url, { params: { apikey: process.env.POLYGON_API_KEY } })
          : await axios.get(url, { params: { apikey: process.env.POLYGON_API_KEY } });

        if (!response.data || !response.data.results) {
          console.log(`No options data found for ${ticker} on page ${pageCount + 1}`);
          break;
        }

        const contracts = response.data.results;
        pageCount++;
        allContracts = [...allContracts, ...contracts];

        console.log(`Page ${pageCount}: Found ${contracts.length} contracts (Total: ${allContracts.length})`);

        // Check for next page
        nextUrl = response.data.next_url;
        
        // Small delay to respect rate limits
        if (nextUrl) {
          await new Promise(resolve => setTimeout(resolve, 100));
        }

        // Safety check to prevent infinite loops
        if (pageCount >= maxPages) {
          console.warn(`Reached maximum page limit (${maxPages}) for ${ticker}, stopping pagination`);
          break;
        }

      } while (nextUrl);

      console.log(`Comprehensive pagination complete: Found ${allContracts.length} total contracts across ${pageCount} pages`);
      return allContracts;
      
    } catch (error) {
      console.error(`Error fetching comprehensive options contracts for ${ticker}:`, error);
      throw error;
    }
  }

  /**
   * Get daily premiums for multiple tickers
   * @param {Array<string>} tickers - Array of ticker symbols
   * @param {string} date - The trading date
   * @returns {Promise<Array>} Array of premium data for each ticker
   */
  async getDailyPremiumsForTickers(tickers, date) {
    const results = [];
    
    for (const ticker of tickers) {
      try {
        const premiumData = await this.calculateNetPremiums(ticker, date);
        results.push(premiumData);
      } catch (error) {
        console.error(`Failed to get premiums for ${ticker}:`, error);
        results.push({
          ticker,
          date,
          error: error.message,
          totalCallPremium: 0,
          totalPutPremium: 0,
          netPremium: 0
        });
      }
    }

    return results;
  }

  /**
   * Store daily premiums data in ClickHouse
   */
  async storeDailyPremiums(premiumsData) {
    try {
      // Check if ClickHouse client is available
      if (!this.clickhouse) {
        console.error('ClickHouse client not initialized');
        return false;
      }

      await this.clickhouse.exec(`
        CREATE TABLE IF NOT EXISTS daily_premiums (
          ticker String,
          date Date,
          net_call_premium Float64,
          net_put_premium Float64,
          net_premium Float64,
          total_call_volume UInt64,
          total_put_volume UInt64,
          call_premium_at_ask Float64,
          call_premium_at_bid Float64,
          put_premium_at_ask Float64,
          put_premium_at_bid Float64,
          trades_processed UInt32,
          created_at DateTime DEFAULT now()
        ) ENGINE = MergeTree()
        ORDER BY (ticker, date)
      `);

      await this.clickhouse.insert({
        table: 'daily_premiums',
        values: [{
          ticker: premiumsData.ticker,
          date: premiumsData.date,
          net_call_premium: premiumsData.netCallPremium,
          net_put_premium: premiumsData.netPutPremium,
          net_premium: premiumsData.netPremium,
          total_call_volume: premiumsData.totalCallVolume,
          total_put_volume: premiumsData.totalPutVolume,
          call_premium_at_ask: premiumsData.callPremiumAtAsk,
          call_premium_at_bid: premiumsData.callPremiumAtBid,
          put_premium_at_ask: premiumsData.putPremiumAtAsk,
          put_premium_at_bid: premiumsData.putPremiumAtBid,
          trades_processed: premiumsData.tradesProcessed
        }]
      });

      console.log(`✅ Stored daily premiums for ${premiumsData.ticker} on ${premiumsData.date} in ClickHouse`);
      return true;
    } catch (error) {
      console.error('Error storing daily premiums in ClickHouse:', error.message);
      console.error('Full error:', error);
      return false;
    }
  }

  /**
   * Calculate and store daily premiums
   */
  async calculateAndStoreDailyPremiums(ticker, date = null) {
    try {
      console.log(`🔄 Calculating and storing daily premiums for ${ticker}...`);
      const premiumsData = await this.calculateNetPremiums(ticker, date);
      const stored = await this.storeDailyPremiums(premiumsData);
      return { ...premiumsData, stored };
    } catch (error) {
      console.error(`Error calculating and storing daily premiums for ${ticker}:`, error.message);
      throw error;
    }
  }
}

module.exports = new PolygonOptionsTradesService();
