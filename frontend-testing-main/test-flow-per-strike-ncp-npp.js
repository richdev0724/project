import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testFlowPerStrikeForNCPNPP() {
  try {
    console.log('🧪 Testing flow-per-strike endpoint to calculate NCP and NPP from ask/bid sides...');
    
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
      
      console.log('\n📊 SAMPLE STRIKE DATA:');
      console.log('First 3 strikes:');
      flowData.slice(0, 3).forEach((strike, i) => {
        console.log(`\nStrike ${i + 1}:`);
        console.log(`  Strike: ${strike.strike}`);
        console.log(`  Call Premium: ${strike.call_premium}`);
        console.log(`  Call Ask Side: ${strike.call_premium_ask_side}`);
        console.log(`  Call Bid Side: ${strike.call_premium_bid_side}`);
        console.log(`  Put Premium: ${strike.put_premium}`);
        console.log(`  Put Ask Side: ${strike.put_premium_ask_side}`);
        console.log(`  Put Bid Side: ${strike.put_premium_bid_side}`);
      });
      
      // Calculate totals by summing all strikes
      let totalCallPremium = 0;
      let totalPutPremium = 0;
      let totalCallPremiumAskSide = 0;
      let totalCallPremiumBidSide = 0;
      let totalPutPremiumAskSide = 0;
      let totalPutPremiumBidSide = 0;
      
      flowData.forEach(strike => {
        totalCallPremium += parseFloat(strike.call_premium) || 0;
        totalPutPremium += parseFloat(strike.put_premium) || 0;
        totalCallPremiumAskSide += parseFloat(strike.call_premium_ask_side) || 0;
        totalCallPremiumBidSide += parseFloat(strike.call_premium_bid_side) || 0;
        totalPutPremiumAskSide += parseFloat(strike.put_premium_ask_side) || 0;
        totalPutPremiumBidSide += parseFloat(strike.put_premium_bid_side) || 0;
      });
      
      // Calculate net premiums as per UW logic: Ask Side - Bid Side
      const calculatedNCP = totalCallPremiumAskSide - totalCallPremiumBidSide;
      const calculatedNPP = totalPutPremiumAskSide - totalPutPremiumBidSide;
      const calculatedNetPremium = calculatedNCP - calculatedNPP;
      
      console.log('\n📈 CALCULATED TOTALS:');
      console.log(`Total Call Premium: $${(totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Total Put Premium: $${(totalPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Total Call Premium Ask Side: $${(totalCallPremiumAskSide / 1000000).toFixed(2)}M`);
      console.log(`Total Call Premium Bid Side: $${(totalCallPremiumBidSide / 1000000).toFixed(2)}M`);
      console.log(`Total Put Premium Ask Side: $${(totalPutPremiumAskSide / 1000000).toFixed(2)}M`);
      console.log(`Total Put Premium Bid Side: $${(totalPutPremiumBidSide / 1000000).toFixed(2)}M`);
      
      console.log('\n🧮 NET PREMIUMS (Ask Side - Bid Side):');
      console.log(`NCP (Net Call Premium): $${(calculatedNCP / 1000000).toFixed(2)}M`);
      console.log(`NPP (Net Put Premium): $${(calculatedNPP / 1000000).toFixed(2)}M`);
      console.log(`Net Premium (NCP - NPP): $${(calculatedNetPremium / 1000000).toFixed(2)}M`);
      
      console.log('\n🎯 YOUR EXPECTED VALUES FOR 10/22:');
      console.log('NCP = -$26.49M');
      console.log('NPP = $3.44M');
      console.log('Put Premium = $76.92M');
      console.log('Call Premium = $244.61M');
      console.log('Net Premium = NCP - NPP = -$26.49M - $3.44M = -$29.93M');
      
      console.log('\n📊 COMPARISON:');
      console.log(`NCP: Expected -$26.49M, Got $${(calculatedNCP / 1000000).toFixed(2)}M`);
      console.log(`NPP: Expected $3.44M, Got $${(calculatedNPP / 1000000).toFixed(2)}M`);
      console.log(`Call Premium: Expected $244.61M, Got $${(totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Put Premium: Expected $76.92M, Got $${(totalPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Premium: Expected -$29.93M, Got $${(calculatedNetPremium / 1000000).toFixed(2)}M`);
      
      // Check if values match
      const ncpMatch = Math.abs(-26490000 - calculatedNCP) < 1000000;
      const nppMatch = Math.abs(3440000 - calculatedNPP) < 1000000;
      const callPremMatch = Math.abs(244610000 - totalCallPremium) < 1000000;
      const putPremMatch = Math.abs(76920000 - totalPutPremium) < 1000000;
      const netPremMatch = Math.abs(-29930000 - calculatedNetPremium) < 1000000;
      
      console.log('\n✅ ACCURACY CHECK:');
      console.log(`NCP: ${ncpMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`NPP: ${nppMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Call Premium: ${callPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Put Premium: ${putPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Net Premium: ${netPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      
      if (ncpMatch && nppMatch && callPremMatch && putPremMatch && netPremMatch) {
        console.log('\n🎉 SUCCESS: All values match expected results!');
      } else {
        console.log('\n⚠️ Some values still need adjustment');
      }
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

testFlowPerStrikeForNCPNPP();

