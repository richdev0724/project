const bulkCompanyProfileService = require('./src/services/bulkCompanyProfileService');
const companyProfileStorageService = require('./src/services/companyProfileStorageService');
const allTickersService = require('./src/services/allTickersService');

async function storeAllExistingProfiles() {
  console.log('🚀 Starting to store all existing company profiles in ClickHouse...');
  
  try {
    // Get all tickers
    console.log('📊 Fetching all US tickers...');
    const allTickers = await allTickersService.getAllUSTickers();
    console.log(`📈 Found ${allTickers.length} tickers to process`);
    
    let successCount = 0;
    let failCount = 0;
    const batchSize = 50;
    
    // Process in batches
    for (let i = 0; i < allTickers.length; i += batchSize) {
      const batch = allTickers.slice(i, i + batchSize);
      console.log(`🔄 Processing batch ${Math.floor(i / batchSize) + 1}/${Math.ceil(allTickers.length / batchSize)} (${batch.length} tickers)`);
      
      const batchPromises = batch.map(async (ticker) => {
        try {
          // Fetch profile from FMP API
          const profile = await bulkCompanyProfileService.fetchCompanyProfileWithRetry(ticker);
          
          if (profile && profile.symbol && profile.companyName) {
            // Format profile data
            const formattedProfile = {
              ticker: profile.symbol,
              companyName: profile.companyName,
              description: profile.description || '',
              industry: profile.industry || '',
              sector: profile.sector || '',
              ceo: profile.ceo || '',
              website: profile.website || '',
              employees: profile.fullTimeEmployees || 0,
              marketCap: profile.mktCap || 0,
              country: profile.country || '',
              city: profile.city || '',
              state: profile.state || '',
              address: profile.address || '',
              phone: profile.phone || '',
              exchange: profile.exchangeShortName || '',
              currency: profile.currency || 'USD',
              isEtf: profile.isEtf || false,
              isActivelyTrading: profile.isActivelyTrading || true
            };
            
            // Store in ClickHouse
            const stored = await companyProfileStorageService.storeCompanyProfile(formattedProfile);
            if (stored) {
              successCount++;
              console.log(`✅ Stored ${ticker}: ${profile.companyName}`);
            } else {
              failCount++;
              console.warn(`⚠️ Failed to store ${ticker}: ${profile.companyName}`);
            }
          } else {
            failCount++;
            console.warn(`⚠️ No profile data for ${ticker}`);
          }
        } catch (error) {
          failCount++;
          console.error(`❌ Error processing ${ticker}:`, error.message);
        }
      });
      
      await Promise.allSettled(batchPromises);
      
      // Small delay between batches
      if (i + batchSize < allTickers.length) {
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }
    
    console.log('\n🎉 Storage complete!');
    console.log(`✅ Successfully stored: ${successCount} profiles`);
    console.log(`❌ Failed: ${failCount} profiles`);
    console.log(`📊 Success rate: ${((successCount / (successCount + failCount)) * 100).toFixed(2)}%`);
    
    // Get final storage statistics
    const stats = await companyProfileStorageService.getStorageStats();
    console.log('\n📈 Final ClickHouse Storage Statistics:');
    console.log(`Total Profiles: ${stats.totalProfiles}`);
    console.log(`Compressed Size: ${stats.compressedSize}`);
    console.log(`Uncompressed Size: ${stats.uncompressedSize}`);
    
  } catch (error) {
    console.error('💥 Error in storeAllExistingProfiles:', error);
  }
}

// Run the script
storeAllExistingProfiles();
