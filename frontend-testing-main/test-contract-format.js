// Test script to understand contract format
const optionsFlowService = require('./backend/src/services/optionsFlowService');

// Test the parseOptionSymbol function with sample data
const sampleContracts = [
  'O:AAPL251017C00200000',  // Apple call
  'O:AAPL251017P00200000',  // Apple put
  'O:TSLA250117C00300000',  // Tesla call
  'O:SPY250321P00500000'    // SPY put
];

console.log('Testing contract symbol parsing:');
console.log('=====================================');

sampleContracts.forEach(contract => {
  console.log(`\nContract: ${contract}`);
  const parsed = optionsFlowService.parseOptionSymbol(contract);
  console.log(`Parsed:`, parsed);
  console.log(`Type: ${parsed.type} (${parsed.type === 'C' ? 'Call' : 'Put'})`);
});

// Test the regex patterns
console.log('\n\nTesting regex patterns:');
console.log('========================');

const callPattern = /O:[A-Z]+[0-9]{6}C[0-9]{8}/;
const putPattern = /O:[A-Z]+[0-9]{6}P[0-9]{8}/;

sampleContracts.forEach(contract => {
  console.log(`\nContract: ${contract}`);
  console.log(`Matches call pattern: ${callPattern.test(contract)}`);
  console.log(`Matches put pattern: ${putPattern.test(contract)}`);
});


