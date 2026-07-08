const axios = require('axios');

async function testUWDirect() {
  try {
    const url = 'https://api.unusualwhales.com/api/stock/META/net-prem-ticks';
    const token = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';
    
    console.log('Testing UW API directly...');
    console.log('URL:', url);
    
    const response = await axios.get(url, {
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${token}`
      }
    });
    
    console.log('Status:', response.status);
    console.log('Data keys:', Object.keys(response.data));
    
    if (response.data.data && response.data.data.length > 0) {
      console.log('Number of ticks:', response.data.data.length);
      console.log('First tick:', response.data.data[0]);
      console.log('Last tick:', response.data.data[response.data.data.length - 1]);
    } else {
      console.log('No data in response');
      console.log('Full response:', JSON.stringify(response.data, null, 2));
    }
    
  } catch (error) {
    console.error('Error:', error.message);
    if (error.response) {
      console.error('Status:', error.response.status);
      console.error('Data:', error.response.data);
    }
  }
}

testUWDirect();
