// Test script for monthly update system
import { monthlyUpdateService } from './src/api/monthly-update.service';

async function testMonthlyUpdateSystem() {
  console.log('🧪 Testing Monthly Update System...\n');

  // Test 1: Get update status
  console.log('Test 1: Get update status');
  const status = monthlyUpdateService.getUpdateStatus();
  console.log('📊 Update Status:', {
    isRunning: status.isRunning,
    lastUpdate: status.lastUpdate,
    nextUpdate: status.nextUpdate,
    totalTickers: status.totalTickers,
  });

  // Test 2: Get common tickers
  console.log('\nTest 2: Get common tickers');
  const tickers = monthlyUpdateService.getCommonTickers();
  console.log(`📈 Total tickers: ${tickers.length}`);
  console.log(`📈 First 10 tickers: ${tickers.slice(0, 10).join(', ')}`);

  // Test 3: Add/remove ticker
  console.log('\nTest 3: Add/remove ticker');
  monthlyUpdateService.addTickerToUpdateList('TEST');
  console.log('✅ Added TEST ticker');
  
  const updatedTickers = monthlyUpdateService.getCommonTickers();
  console.log(`📈 Total tickers after add: ${updatedTickers.length}`);
  
  monthlyUpdateService.removeTickerFromUpdateList('TEST');
  console.log('✅ Removed TEST ticker');
  
  const finalTickers = monthlyUpdateService.getCommonTickers();
  console.log(`📈 Total tickers after remove: ${finalTickers.length}`);

  // Test 4: Force update (commented out to avoid API calls)
  console.log('\nTest 4: Force update (simulation)');
  console.log('⚠️ Skipping actual update to avoid API calls');
  console.log('💡 To test actual update, uncomment the line below:');
  console.log('// await monthlyUpdateService.forceUpdate();');

  // Test 5: Check if update should run
  console.log('\nTest 5: Check if update should run');
  try {
    await monthlyUpdateService.checkAndRunUpdate();
    console.log('✅ Update check completed');
  } catch (error) {
    console.log('ℹ️ Update check result:', error.message);
  }

  // Test 6: Final status
  console.log('\nTest 6: Final status');
  const finalStatus = monthlyUpdateService.getUpdateStatus();
  console.log('📊 Final Status:', {
    isRunning: finalStatus.isRunning,
    lastUpdate: finalStatus.lastUpdate,
    nextUpdate: finalStatus.nextUpdate,
    totalTickers: finalStatus.totalTickers,
    updatedTickers: finalStatus.updatedTickers,
    failedTickers: finalStatus.failedTickers,
  });

  console.log('\n🎉 Monthly update system test completed!');
}

// Run the test
testMonthlyUpdateSystem().catch(console.error);
