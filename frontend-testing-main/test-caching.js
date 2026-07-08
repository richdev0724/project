// Test script to verify company profile caching
import { companyProfileService } from './src/api/company-profile.service';

async function testCaching() {
  console.log('🧪 Testing Company Profile Caching System...\n');

  // Test 1: First fetch (should hit API)
  console.log('Test 1: First fetch for META (should hit API)');
  const start1 = Date.now();
  const profile1 = await companyProfileService.getCompanyProfile('META');
  const time1 = Date.now() - start1;
  console.log(`✅ Fetched in ${time1}ms`);
  console.log(`Company: ${profile1?.companyName}`);
  console.log(`CEO: ${profile1?.ceo}\n`);

  // Test 2: Second fetch (should hit cache)
  console.log('Test 2: Second fetch for META (should hit cache)');
  const start2 = Date.now();
  const profile2 = await companyProfileService.getCompanyProfile('META');
  const time2 = Date.now() - start2;
  console.log(`✅ Fetched in ${time2}ms (${time1 > time2 ? 'faster!' : 'slower'})`);
  console.log(`Company: ${profile2?.companyName}\n`);

  // Test 3: Cache stats
  console.log('Test 3: Cache Statistics');
  const stats = companyProfileService.getCacheStats();
  console.log(`📊 Total cached: ${stats.totalCached}`);
  console.log(`📊 Cache size: ${stats.cacheSize} bytes`);
  console.log(`📊 Last updated: ${stats.lastUpdated}\n`);

  // Test 4: Preload multiple profiles
  console.log('Test 4: Preloading multiple profiles');
  const start3 = Date.now();
  await companyProfileService.preloadProfiles(['AAPL', 'TSLA', 'MSFT']);
  const time3 = Date.now() - start3;
  console.log(`✅ Preloaded 3 profiles in ${time3}ms\n`);

  // Test 5: Final cache stats
  console.log('Test 5: Final Cache Statistics');
  const finalStats = companyProfileService.getCacheStats();
  console.log(`📊 Total cached: ${finalStats.totalCached}`);
  console.log(`📊 Cache size: ${finalStats.cacheSize} bytes`);

  console.log('\n🎉 Caching system test completed!');
}

// Run the test
testCaching().catch(console.error);
