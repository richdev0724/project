const fs = require('fs');
const path = require('path');

/**
 * Script to import historical politician trading data from Capitol Gains project
 * 
 * Usage:
 * 1. Clone capitol-gains: git clone https://github.com/sourcesync/capitol-gains.git
 * 2. Place house.json and senate.json in backend/data/ directory
 * 3. Run: node scripts/import-politician-trades.js
 */

// Path to the data files
const HOUSE_DATA_PATH = path.join(__dirname, '../data/house.json');
const SENATE_DATA_PATH = path.join(__dirname, '../data/senate.json');
const OUTPUT_PATH = path.join(__dirname, '../data/politician-trades.json');

/**
 * Clean politician names by removing titles, suffixes, and honorifics
 */
function cleanPoliticianName(firstName, lastName) {
  // Remove common titles and honorifics
  const titlePrefixes = ['Hon', 'Hon.', 'Honorable', 'Dr', 'Dr.', 'Mr', 'Mr.', 'Mrs', 'Mrs.', 'Ms', 'Ms.', 'Miss', 'Sen', 'Sen.', 'Rep', 'Rep.'];
  const suffixPatterns = ['Jr', 'Jr.', 'Sr', 'Sr.', 'II', 'III', 'IV', 'MD', 'M.D.', 'Facs', 'FACS'];
  
  // Clean first name
  let cleanFirst = firstName.trim();
  titlePrefixes.forEach(prefix => {
    const regex = new RegExp(`\\b${prefix}\\b\\.?\\s*`, 'gi');
    cleanFirst = cleanFirst.replace(regex, '');
  });
  
  // Clean last name
  let cleanLast = lastName.trim();
  titlePrefixes.forEach(prefix => {
    const regex = new RegExp(`\\b${prefix}\\b\\.?\\s*`, 'gi');
    cleanLast = cleanLast.replace(regex, '');
  });
  
  // Remove suffixes and anything in parentheses
  cleanLast = cleanLast.replace(/\([^)]*\)/g, ''); // Remove (Bobby)
  suffixPatterns.forEach(suffix => {
    const regex = new RegExp(`,?\\s*\\b${suffix}\\b\\.?\\s*`, 'gi');
    cleanLast = cleanLast.replace(regex, '');
  });
  
  // Remove any remaining commas and extra spaces
  cleanFirst = cleanFirst.replace(/,/g, '').replace(/\s+/g, ' ').trim();
  cleanLast = cleanLast.replace(/,/g, '').replace(/\s+/g, ' ').trim();
  
  // Remove quotes around nicknames (e.g., "Bobby")
  cleanFirst = cleanFirst.replace(/["']/g, '');
  cleanLast = cleanLast.replace(/["']/g, '');
  
  // Remove single letter initials (A., B., etc.)
  cleanFirst = cleanFirst.replace(/\b[A-Z]\.\s*/g, '');
  cleanLast = cleanLast.replace(/\b[A-Z]\.\s*/g, '');
  
  // Handle special cases
  // If first name has multiple words, try to get the actual first name
  const firstParts = cleanFirst.split(/\s+/).filter(p => p.length > 0);
  if (firstParts.length > 1) {
    // Keep the longest part (usually the actual name, not initial)
    cleanFirst = firstParts.reduce((a, b) => a.length >= b.length ? a : b);
  }
  
  // Clean up last name - if it has multiple parts, join them
  const lastParts = cleanLast.split(/\s+/).filter(p => p.length > 0);
  cleanLast = lastParts.join(' ');
  
  // Final cleanup: remove any double spaces
  const finalName = `${cleanFirst} ${cleanLast}`.replace(/\s+/g, ' ').trim();
  
  return finalName;
}

/**
 * Transform Capitol Gains format to our format
 */
function transformTrade(trade, index) {
  // Map transaction types
  const transactionMap = {
    'sale': 'Sale',
    'purchase': 'Purchase',
    'exchange': 'Exchange',
    'sale_partial': 'Sale (Partial)',
    'sale_full': 'Sale (Full)'
  };

  // Format amount range
  const formatAmount = (low, high) => {
    if (!low || !high) return 'Unknown';
    
    const formatValue = (val) => {
      if (val >= 1000000) return `$${(val / 1000000).toFixed(1)}M`;
      if (val >= 1000) return `$${(val / 1000).toFixed(0)}K`;
      return `$${val}`;
    };
    
    return `${formatValue(low)} - ${formatValue(high)}`;
  };

  // Determine party (would need to be enriched with actual party data)
  // For now, we'll leave it as Unknown and can enhance later
  const party = 'Unknown';

  // Determine position
  const position = trade.governing_body === 'HOUSE' 
    ? `Representative` 
    : `Senator`;

  return {
    id: `trade_${index}`,
    politician: cleanPoliticianName(trade.first_name, trade.last_name),
    party: party,
    position: position,
    tradeType: transactionMap[trade.transaction] || 'Unknown',
    ticker: trade.ticker || 'N/A',
    assetName: trade.asset_name ? trade.asset_name.trim() : 'Unknown Asset',
    assetType: trade.asset_type || 'Stock',
    amount: formatAmount(trade.asset_value_low, trade.asset_value_high),
    amountMin: trade.asset_value_low || 0,
    amountMax: trade.asset_value_high || 0,
    tradeDate: trade.transaction_date || null,
    disclosureDate: trade.notification_date || null,
    owner: 'Self', // Capitol Gains doesn't specify owner
    // Additional fields from Capitol Gains
    stockPrice: trade.stock_price,
    shareCount: trade.share_count,
    docId: trade.doc_id,
    description: trade.description,
    governingBody: trade.governing_body
  };
}

/**
 * Main import function
 */
function importData() {
  console.log('🚀 Starting politician trades data import...\n');

  try {
    // Check if data files exist
    if (!fs.existsSync(HOUSE_DATA_PATH)) {
      console.error(`❌ House data not found at: ${HOUSE_DATA_PATH}`);
      console.log('\n📋 To get the data:');
      console.log('1. git clone https://github.com/sourcesync/capitol-gains.git');
      console.log('2. cp capitol-gains/data/parsed_disclosures/house.json backend/data/');
      console.log('3. cp capitol-gains/data/parsed_disclosures/senate.json backend/data/');
      return;
    }

    if (!fs.existsSync(SENATE_DATA_PATH)) {
      console.error(`❌ Senate data not found at: ${SENATE_DATA_PATH}`);
      return;
    }

    // Read and parse data
    console.log('📖 Reading data files...');
    const houseData = JSON.parse(fs.readFileSync(HOUSE_DATA_PATH, 'utf8'));
    const senateData = JSON.parse(fs.readFileSync(SENATE_DATA_PATH, 'utf8'));

    const houseTrades = houseData.disclosures || [];
    const senateTrades = senateData.disclosures || [];

    console.log(`   House trades: ${houseTrades.length.toLocaleString()}`);
    console.log(`   Senate trades: ${senateTrades.length.toLocaleString()}`);
    console.log(`   Total: ${(houseTrades.length + senateTrades.length).toLocaleString()}\n`);

    // Combine and transform
    console.log('🔄 Transforming data...');
    const allTrades = [...houseTrades, ...senateTrades];
    const transformedTrades = allTrades
      .map((trade, index) => transformTrade(trade, index))
      .filter(trade => trade.ticker && trade.ticker !== 'N/A') // Filter out trades without tickers
      .sort((a, b) => new Date(b.tradeDate) - new Date(a.tradeDate)); // Sort by date, newest first

    console.log(`   Transformed: ${transformedTrades.length.toLocaleString()} trades with valid tickers\n`);

    // Get statistics
    const politicians = [...new Set(transformedTrades.map(t => t.politician))];
    const tickers = [...new Set(transformedTrades.map(t => t.ticker))];
    const dateRange = {
      earliest: transformedTrades[transformedTrades.length - 1]?.tradeDate,
      latest: transformedTrades[0]?.tradeDate
    };

    console.log('📊 Statistics:');
    console.log(`   Unique politicians: ${politicians.length}`);
    console.log(`   Unique tickers: ${tickers.length}`);
    console.log(`   Date range: ${dateRange.earliest} to ${dateRange.latest}\n`);

    // Create output directory if it doesn't exist
    const outputDir = path.dirname(OUTPUT_PATH);
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }

    // Write transformed data
    console.log('💾 Writing transformed data...');
    fs.writeFileSync(
      OUTPUT_PATH, 
      JSON.stringify(transformedTrades, null, 2),
      'utf8'
    );

    console.log(`✅ Successfully wrote ${transformedTrades.length.toLocaleString()} trades to:`);
    console.log(`   ${OUTPUT_PATH}\n`);

    // Write statistics file
    const statsPath = path.join(__dirname, '../data/politician-trades-stats.json');
    const stats = {
      totalTrades: transformedTrades.length,
      uniquePoliticians: politicians.length,
      uniqueTickers: tickers.length,
      dateRange,
      topPoliticians: getTopPoliticians(transformedTrades, 10),
      topTickers: getTopTickers(transformedTrades, 10),
      tradesByYear: getTradesByYear(transformedTrades),
      lastUpdated: new Date().toISOString()
    };

    fs.writeFileSync(
      statsPath,
      JSON.stringify(stats, null, 2),
      'utf8'
    );

    console.log('📈 Statistics file written to:');
    console.log(`   ${statsPath}\n`);

    console.log('🎉 Import complete!\n');
    console.log('📝 Next steps:');
    console.log('1. Update politicianTradesService.js to load from politician-trades.json');
    console.log('2. Restart your backend server');
    console.log('3. Refresh the Politicians page\n');

  } catch (error) {
    console.error('❌ Error during import:', error);
  }
}

/**
 * Get top politicians by trade count
 */
function getTopPoliticians(trades, limit = 10) {
  const counts = {};
  trades.forEach(trade => {
    counts[trade.politician] = (counts[trade.politician] || 0) + 1;
  });
  
  return Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([politician, count]) => ({ politician, tradeCount: count }));
}

/**
 * Get top tickers by trade count
 */
function getTopTickers(trades, limit = 10) {
  const counts = {};
  trades.forEach(trade => {
    if (trade.ticker && trade.ticker !== 'N/A') {
      counts[trade.ticker] = (counts[trade.ticker] || 0) + 1;
    }
  });
  
  return Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([ticker, count]) => ({ ticker, tradeCount: count }));
}

/**
 * Get trades grouped by year
 */
function getTradesByYear(trades) {
  const byYear = {};
  trades.forEach(trade => {
    if (trade.tradeDate) {
      const year = trade.tradeDate.split('-')[0];
      byYear[year] = (byYear[year] || 0) + 1;
    }
  });
  
  return Object.entries(byYear)
    .sort((a, b) => b[0] - a[0])
    .reduce((obj, [year, count]) => {
      obj[year] = count;
      return obj;
    }, {});
}

// Run the import
importData();

