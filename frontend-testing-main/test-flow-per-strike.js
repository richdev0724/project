import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testFlowPerStrike() {
  try {
    console.log('🧪 Testing flow-per-strike endpoint directly...');
    
    const url = `${UNUSUAL_WHALES_API_URL}/stock/META/flow-per-strike`;
    const params = { date: '2025-10-22' };
    
    console.log(`🔗 URL: ${url}`);
    console.log(`📅 Date: 2025-10-22`);
    
    const response = await axios.get(url, {
      params,
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    console.log('✅ Response received');
    console.log(`📊 Status: ${response.status}`);
    console.log(`📊 Data length: ${response.data?.data?.length || 'No data'}`);
    
    if (response.data?.data?.length > 0) {
      const flowData = response.data.data;
      
      console.log('\n📊 SAMPLE DATA:');
      console.log('First 3 strikes:');
      flowData.slice(0, 3).forEach((strike, i) => {
        console.log(`\nStrike ${i + 1}:`);
        console.log(`  Strike: ${strike.strike}`);
        console.log(`  Call Premium: ${strike.call_premium}`);
        console.log(`  Put Premium: ${strike.put_premium}`);
        console.log(`  Call Premium Ask Side: ${strike.call_premium_ask_side}`);
        console.log(`  Call Premium Bid Side: ${strike.call_premium_bid_side}`);
        console.log(`  Put Premium Ask Side: ${strike.put_premium_ask_side}`);
        console.log(`  Put Premium Bid Side: ${strike.put_premium_bid_side}`);
        console.log(`  Date: ${strike.date}`);
        console.log(`  Ticker: ${strike.ticker}`);
      });
      
      // Calculate totals
      let totalCallPremium = 0;
      let totalPutPremium = 0;
      let callPremiumAskSide = 0;
      let callPremiumBidSide = 0;
      let putPremiumAskSide = 0;
      let putPremiumBidSide = 0;
      
      flowData.forEach(strike => {
        totalCallPremium += parseFloat(strike.call_premium) || 0;
        totalPutPremium += parseFloat(strike.put_premium) || 0;
        callPremiumAskSide += parseFloat(strike.call_premium_ask_side) || 0;
        callPremiumBidSide += parseFloat(strike.call_premium_bid_side) || 0;
        putPremiumAskSide += parseFloat(strike.put_premium_ask_side) || 0;
        putPremiumBidSide += parseFloat(strike.put_premium_bid_side) || 0;
      });
      
      console.log('\n📈 CALCULATED TOTALS:');
      console.log(`Total Call Premium: $${(totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Total Put Premium: $${(totalPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Call Premium Ask Side: $${(callPremiumAskSide / 1000000).toFixed(2)}M`);
      console.log(`Call Premium Bid Side: $${(callPremiumBidSide / 1000000).toFixed(2)}M`);
      console.log(`Put Premium Ask Side: $${(putPremiumAskSide / 1000000).toFixed(2)}M`);
      console.log(`Put Premium Bid Side: $${(putPremiumBidSide / 1000000).toFixed(2)}M`);
      
      // Calculate net premiums
      const netCallPremium = callPremiumAskSide - callPremiumBidSide;
      const netPutPremium = putPremiumAskSide - putPremiumBidSide;
      
      console.log('\n🧮 NET PREMIUMS:');
      console.log(`Net Call Premium: $${(netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Put Premium: $${(netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Premium: $${((netCallPremium - Math.abs(netPutPremium)) / 1000000).toFixed(2)}M`);
      
      console.log('\n🎯 YOUR EXPECTED VALUES:');
      console.log('NCP = -$26.49M');
      console.log('NPP = $3.44M');
      console.log('Put Premium = $76.92M');
      console.log('Call Premium = $244.61M');
      console.log('Net Premium = -$10,464,052');
    } else {
      console.log('❌ No data returned from flow-per-strike endpoint');
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
  }
}

testFlowPerStrike();

