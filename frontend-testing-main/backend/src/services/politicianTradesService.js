const axios = require('axios');
const fs = require('fs');
const path = require('path');

/**
 * Service for fetching politician trading data
 * Uses historical data from Capitol Gains project
 */

class PoliticianTradesService {
  constructor() {
    this.baseUrl = 'https://www.capitoltrades.com';
    // Path to the imported data
    this.dataPath = path.join(__dirname, '../../data/politician-trades.json');
    this.statsPath = path.join(__dirname, '../../data/politician-trades-stats.json');
    
    // Cache to store fetched data
    this.cache = {
      trades: null,
      stats: null,
      lastFetched: null,
      expiresIn: 1000 * 60 * 60 * 6 // 6 hours
    };
    
    // Load data on initialization
    this.loadDataFromFile();
  }
  
  /**
   * Load trades from JSON file
   */
  loadDataFromFile() {
    try {
      if (fs.existsSync(this.dataPath)) {
        const data = fs.readFileSync(this.dataPath, 'utf8');
        this.cache.trades = JSON.parse(data);
        this.cache.lastFetched = Date.now();
        console.log(`✅ Loaded ${this.cache.trades.length.toLocaleString()} politician trades from file`);
        
        // Load stats if available
        if (fs.existsSync(this.statsPath)) {
          const statsData = fs.readFileSync(this.statsPath, 'utf8');
          this.cache.stats = JSON.parse(statsData);
        }
      } else {
        console.log('⚠️  No historical data file found, using mock data');
        this.cache.trades = this.getMockTrades();
        this.cache.lastFetched = Date.now();
      }
    } catch (error) {
      console.error('Error loading politician trades from file:', error);
      this.cache.trades = this.getMockTrades();
      this.cache.lastFetched = Date.now();
    }
  }

  /**
   * Check if cache is still valid
   */
  isCacheValid() {
    if (!this.cache.trades || !this.cache.lastFetched) {
      return false;
    }
    return Date.now() - this.cache.lastFetched < this.cache.expiresIn;
  }

  /**
   * Get all politician trades
   * Returns historical data loaded from file
   */
  async getAllTrades() {
    // If cache is not loaded, try to load it
    if (!this.cache.trades) {
      this.loadDataFromFile();
    }
    
    return this.cache.trades || [];
  }
  
  /**
   * Get statistics about the trades
   */
  async getStatistics() {
    return this.cache.stats || {
      totalTrades: this.cache.trades?.length || 0,
      uniquePoliticians: [...new Set(this.cache.trades?.map(t => t.politician) || [])].length,
      uniqueTickers: [...new Set(this.cache.trades?.map(t => t.ticker) || [])].length
    };
  }

  /**
   * Get trades for a specific politician
   */
  async getPoliticianTrades(politicianName) {
    const allTrades = await this.getAllTrades();
    return allTrades.filter(trade => 
      trade.politician.toLowerCase() === politicianName.toLowerCase()
    );
  }

  /**
   * Get portfolio data for a specific politician
   */
  async getPoliticianPortfolio(politicianName) {
    const trades = await this.getPoliticianTrades(politicianName);
    
    if (!trades || trades.length === 0) {
      return null;
    }
    
    // Get politician info from first trade
    const firstTrade = trades[0];
    
    // Calculate portfolio metrics from trades
    const holdings = this.calculateHoldings(trades);
    const metrics = this.calculateMetrics(trades, holdings);
    
    return {
      politician: politicianName,
      party: firstTrade.party || 'Unknown',
      position: firstTrade.position || 'Unknown',
      state: firstTrade.position?.split(', ')[1] || 'Unknown',
      holdings,
      metrics,
      historicalValue: this.generateHistoricalValue(holdings),
      sectorAllocation: this.calculateSectorAllocation(holdings)
    };
  }

  /**
   * Calculate holdings from trades
   */
  calculateHoldings(trades) {
    const holdingsMap = new Map();
    
    trades.forEach(trade => {
      const key = trade.ticker;
      if (!holdingsMap.has(key)) {
        holdingsMap.set(key, {
          ticker: trade.ticker,
          assetName: trade.assetName,
          assetType: trade.assetType,
          sector: this.getSector(trade.ticker),
          shares: 0,
          avgCost: 0,
          totalCost: 0,
          transactions: []
        });
      }
      
      const holding = holdingsMap.get(key);
      holding.transactions.push(trade);
      
      if (trade.tradeType === 'Purchase') {
        holding.shares += trade.amountMin / 100; // Estimate shares
        holding.totalCost += trade.amountMin;
      } else if (trade.tradeType === 'Sale') {
        holding.shares -= trade.amountMin / 100;
      }
    });
    
    return Array.from(holdingsMap.values()).filter(h => h.shares > 0);
  }

  /**
   * Calculate metrics from trades and holdings
   */
  calculateMetrics(trades, holdings) {
    const totalValue = holdings.reduce((sum, h) => sum + (h.shares * 150), 0); // Estimate current value
    
    return {
      totalValue,
      totalGainLoss: totalValue * 0.15, // Estimate 15% gain
      percentGainLoss: 15,
      totalTrades: trades.length,
      winRate: 65,
      avgTradeSize: trades.reduce((sum, t) => sum + t.amountMin, 0) / trades.length,
      avgDaysToDisclose: 20,
      daysInOffice: 3650,
      committees: ['Finance Committee', 'Banking Committee']
    };
  }

  /**
   * Generate historical value data
   */
  generateHistoricalValue(holdings) {
    const months = 12;
    const data = [];
    const baseValue = holdings.reduce((sum, h) => sum + (h.shares * 100), 0);
    
    for (let i = 0; i < months; i++) {
      const date = new Date();
      date.setMonth(date.getMonth() - (months - i));
      data.push({
        date: date.toISOString().split('T')[0],
        value: baseValue * (0.85 + (i * 0.025)), // Gradual increase
        sp500: 100 + (i * 2) // Mock S&P 500 comparison
      });
    }
    
    return data;
  }

  /**
   * Calculate sector allocation
   */
  calculateSectorAllocation(holdings) {
    const sectors = {};
    holdings.forEach(h => {
      if (!sectors[h.sector]) {
        sectors[h.sector] = 0;
      }
      sectors[h.sector] += h.shares * 150; // Estimate value
    });
    
    const total = Object.values(sectors).reduce((sum, v) => sum + v, 0);
    
    return Object.entries(sectors).map(([sector, value]) => ({
      sector,
      value,
      percentage: (value / total) * 100
    }));
  }

  /**
   * Get sector for a ticker (simplified mapping)
   */
  getSector(ticker) {
    const sectorMap = {
      'NVDA': 'Technology',
      'AAPL': 'Technology',
      'MSFT': 'Technology',
      'GOOGL': 'Technology',
      'META': 'Technology',
      'AMZN': 'Consumer Discretionary',
      'TSLA': 'Consumer Discretionary',
      'NFLX': 'Communication Services',
      'JPM': 'Financials',
      'BAC': 'Financials',
      'XOM': 'Energy',
      'UNH': 'Healthcare',
      'CVS': 'Healthcare',
      'COIN': 'Financials',
      'SPY': 'ETF',
      'QQQ': 'ETF'
    };
    return sectorMap[ticker] || 'Other';
  }

  /**
   * Enhanced mock data - will be replaced with real API
   */
  getMockTrades() {
    const currentDate = new Date();
    const getRecentDate = (daysAgo) => {
      const date = new Date(currentDate);
      date.setDate(date.getDate() - daysAgo);
      return date.toISOString().split('T')[0];
    };

    return [
      // Nancy Pelosi - Tech heavy portfolio
      {
        id: '1',
        politician: 'Nancy Pelosi',
        party: 'Democrat',
        position: 'Representative, CA-11',
        tradeType: 'Purchase',
        ticker: 'NVDA',
        assetName: 'NVIDIA Corporation',
        assetType: 'Stock',
        amount: '$1M - $5M',
        amountMin: 1000000,
        amountMax: 5000000,
        tradeDate: getRecentDate(25),
        disclosureDate: getRecentDate(10),
        owner: 'Spouse',
      },
      {
        id: '2',
        politician: 'Nancy Pelosi',
        party: 'Democrat',
        position: 'Representative, CA-11',
        tradeType: 'Purchase',
        ticker: 'MSFT',
        assetName: 'Microsoft Corporation',
        assetType: 'Stock',
        amount: '$500K - $1M',
        amountMin: 500000,
        amountMax: 1000000,
        tradeDate: getRecentDate(30),
        disclosureDate: getRecentDate(15),
        owner: 'Spouse',
      },
      {
        id: '3',
        politician: 'Nancy Pelosi',
        party: 'Democrat',
        position: 'Representative, CA-11',
        tradeType: 'Purchase',
        ticker: 'GOOGL',
        assetName: 'Alphabet Inc Class A',
        assetType: 'Stock',
        amount: '$250K - $500K',
        amountMin: 250000,
        amountMax: 500000,
        tradeDate: getRecentDate(45),
        disclosureDate: getRecentDate(30),
        owner: 'Spouse',
      },
      // Dan Crenshaw - Energy and Defense
      {
        id: '4',
        politician: 'Dan Crenshaw',
        party: 'Republican',
        position: 'Representative, TX-02',
        tradeType: 'Sale',
        ticker: 'TSLA',
        assetName: 'Tesla Inc',
        assetType: 'Stock',
        amount: '$50K - $100K',
        amountMin: 50000,
        amountMax: 100000,
        tradeDate: getRecentDate(20),
        disclosureDate: getRecentDate(8),
        owner: 'Self',
      },
      {
        id: '5',
        politician: 'Dan Crenshaw',
        party: 'Republican',
        position: 'Representative, TX-02',
        tradeType: 'Purchase',
        ticker: 'XOM',
        assetName: 'Exxon Mobil Corporation',
        assetType: 'Stock',
        amount: '$100K - $250K',
        amountMin: 100000,
        amountMax: 250000,
        tradeDate: getRecentDate(35),
        disclosureDate: getRecentDate(20),
        owner: 'Self',
      },
      // Josh Gottheimer - Financials
      {
        id: '6',
        politician: 'Josh Gottheimer',
        party: 'Democrat',
        position: 'Representative, NJ-05',
        tradeType: 'Purchase',
        ticker: 'JPM',
        assetName: 'JPMorgan Chase & Co',
        assetType: 'Stock',
        amount: '$100K - $250K',
        amountMin: 100000,
        amountMax: 250000,
        tradeDate: getRecentDate(18),
        disclosureDate: getRecentDate(7),
        owner: 'Self',
      },
      {
        id: '7',
        politician: 'Josh Gottheimer',
        party: 'Democrat',
        position: 'Representative, NJ-05',
        tradeType: 'Purchase',
        ticker: 'BAC',
        assetName: 'Bank of America Corp',
        assetType: 'Stock',
        amount: '$50K - $100K',
        amountMin: 50000,
        amountMax: 100000,
        tradeDate: getRecentDate(40),
        disclosureDate: getRecentDate(25),
        owner: 'Self',
      },
      // Tommy Tuberville - ETFs and Index Funds
      {
        id: '8',
        politician: 'Tommy Tuberville',
        party: 'Republican',
        position: 'Senator, AL',
        tradeType: 'Purchase',
        ticker: 'SPY',
        assetName: 'SPDR S&P 500 ETF Trust',
        assetType: 'ETF',
        amount: '$250K - $500K',
        amountMin: 250000,
        amountMax: 500000,
        tradeDate: getRecentDate(10),
        disclosureDate: getRecentDate(3),
        owner: 'Self',
      },
      {
        id: '9',
        politician: 'Tommy Tuberville',
        party: 'Republican',
        position: 'Senator, AL',
        tradeType: 'Purchase',
        ticker: 'QQQ',
        assetName: 'Invesco QQQ Trust',
        assetType: 'ETF',
        amount: '$100K - $250K',
        amountMin: 100000,
        amountMax: 250000,
        tradeDate: getRecentDate(22),
        disclosureDate: getRecentDate(12),
        owner: 'Self',
      },
      // Ro Khanna - Tech and Consumer
      {
        id: '10',
        politician: 'Ro Khanna',
        party: 'Democrat',
        position: 'Representative, CA-17',
        tradeType: 'Sale',
        ticker: 'AAPL',
        assetName: 'Apple Inc',
        assetType: 'Stock',
        amount: '$15K - $50K',
        amountMin: 15000,
        amountMax: 50000,
        tradeDate: getRecentDate(22),
        disclosureDate: getRecentDate(10),
        owner: 'Dependent Child',
      },
      {
        id: '11',
        politician: 'Ro Khanna',
        party: 'Democrat',
        position: 'Representative, CA-17',
        tradeType: 'Purchase',
        ticker: 'AMZN',
        assetName: 'Amazon.com Inc',
        assetType: 'Stock',
        amount: '$50K - $100K',
        amountMin: 50000,
        amountMax: 100000,
        tradeDate: getRecentDate(50),
        disclosureDate: getRecentDate(35),
        owner: 'Dependent Child',
      },
      // Marjorie Taylor Greene
      {
        id: '12',
        politician: 'Marjorie Taylor Greene',
        party: 'Republican',
        position: 'Representative, GA-14',
        tradeType: 'Purchase',
        ticker: 'META',
        assetName: 'Meta Platforms Inc',
        assetType: 'Stock',
        amount: '$50K - $100K',
        amountMin: 50000,
        amountMax: 100000,
        tradeDate: getRecentDate(12),
        disclosureDate: getRecentDate(2),
        owner: 'Self',
      },
      {
        id: '13',
        politician: 'Marjorie Taylor Greene',
        party: 'Republican',
        position: 'Representative, GA-14',
        tradeType: 'Sale',
        ticker: 'NFLX',
        assetName: 'Netflix Inc',
        assetType: 'Stock',
        amount: '$15K - $50K',
        amountMin: 15000,
        amountMax: 50000,
        tradeDate: getRecentDate(28),
        disclosureDate: getRecentDate(18),
        owner: 'Self',
      },
      // Pat Toomey - Crypto and Finance
      {
        id: '14',
        politician: 'Pat Toomey',
        party: 'Republican',
        position: 'Senator, PA',
        tradeType: 'Purchase',
        ticker: 'COIN',
        assetName: 'Coinbase Global Inc',
        assetType: 'Stock',
        amount: '$100K - $250K',
        amountMin: 100000,
        amountMax: 250000,
        tradeDate: getRecentDate(15),
        disclosureDate: getRecentDate(5),
        owner: 'Self',
      },
      // Mark Warner - Tech and Healthcare
      {
        id: '15',
        politician: 'Mark Warner',
        party: 'Democrat',
        position: 'Senator, VA',
        tradeType: 'Purchase',
        ticker: 'UNH',
        assetName: 'UnitedHealth Group Inc',
        assetType: 'Stock',
        amount: '$250K - $500K',
        amountMin: 250000,
        amountMax: 500000,
        tradeDate: getRecentDate(33),
        disclosureDate: getRecentDate(18),
        owner: 'Self',
      },
      {
        id: '16',
        politician: 'Mark Warner',
        party: 'Democrat',
        position: 'Senator, VA',
        tradeType: 'Purchase',
        ticker: 'CVS',
        assetName: 'CVS Health Corporation',
        assetType: 'Stock',
        amount: '$50K - $100K',
        amountMin: 50000,
        amountMax: 100000,
        tradeDate: getRecentDate(41),
        disclosureDate: getRecentDate(26),
        owner: 'Spouse',
      },
    ];
  }

  /**
   * Clear cache and reload from file
   */
  clearCache() {
    this.cache.trades = null;
    this.cache.stats = null;
    this.cache.lastFetched = null;
    this.loadDataFromFile();
  }
  
  /**
   * Reload data from file
   */
  reloadData() {
    this.loadDataFromFile();
    return {
      success: true,
      tradesCount: this.cache.trades?.length || 0
    };
  }
}

module.exports = new PoliticianTradesService();

