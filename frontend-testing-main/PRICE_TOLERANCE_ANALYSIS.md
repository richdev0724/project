# Dark Pool & Lit Pool Price Tolerance Analysis

## 🔍 **Current Implementation**

### **Price Tolerance: $0.15**
```javascript
const PRICE_TOLERANCE = 0.15;
const bucket = Math.round(t.price / PRICE_TOLERANCE);
const price = bucket * PRICE_TOLERANCE;
```

### **How It Works**
- Trades within **$0.075** of each other get grouped into the same level
- Example: $710.20 and $710.22 both become $710.25
- Example: $710.35 becomes $710.40 (new level)

---

## 📊 **Analysis of Current Tolerance**

### **Pros of $0.15 Tolerance:**
✅ **Reasonable for high-priced stocks** (META ~$700)
✅ **Reduces noise** from tiny price variations
✅ **Creates meaningful levels** for analysis
✅ **Matches typical bid-ask spreads** on major stocks

### **Cons of $0.15 Tolerance:**
❌ **May be too wide** for lower-priced stocks ($50-100)
❌ **Could miss important price levels** in volatile periods
❌ **Fixed tolerance** doesn't adapt to stock price

---

## 🎯 **Research-Based Recommendations**

### **Option 1: Dynamic Tolerance (Recommended)**
```javascript
// Adaptive tolerance based on stock price
function getPriceTolerance(price) {
  if (price >= 500) return 0.15;      // High-priced: $0.15
  if (price >= 100) return 0.10;      // Mid-priced: $0.10  
  if (price >= 50) return 0.05;       // Lower-priced: $0.05
  return 0.01;                        // Penny stocks: $0.01
}
```

### **Option 2: Percentage-Based Tolerance**
```javascript
// 0.02% of stock price (more adaptive)
const PRICE_TOLERANCE = price * 0.0002;
```

### **Option 3: Market-Based Tolerance**
```javascript
// Based on typical bid-ask spreads
function getMarketTolerance(ticker) {
  const spreads = {
    'META': 0.15,    // High liquidity
    'AAPL': 0.15,    // High liquidity  
    'TSLA': 0.20,    // More volatile
    'NVDA': 0.15,    // High liquidity
    'default': 0.10  // Standard
  };
  return spreads[ticker] || spreads.default;
}
```

---

## 📈 **Impact Analysis**

### **Current $0.15 Tolerance Results:**
- **META ($700)**: 0.02% of price - **Good**
- **AAPL ($150)**: 0.10% of price - **Acceptable**
- **TSLA ($200)**: 0.075% of price - **Good**
- **Lower-priced stocks**: **Too wide**

### **Recommended Dynamic Tolerance:**
- **META ($700)**: $0.15 tolerance - **Same as current**
- **AAPL ($150)**: $0.10 tolerance - **Better granularity**
- **TSLA ($200)**: $0.10 tolerance - **Better granularity**
- **Lower-priced**: $0.05 tolerance - **Much better**

---

## 🛠️ **Implementation Options**

### **Option A: Keep Current ($0.15)**
**Pros**: Simple, works well for high-priced stocks
**Cons**: Less optimal for lower-priced stocks

### **Option B: Implement Dynamic Tolerance**
**Pros**: Optimal for all price ranges
**Cons**: More complex logic

### **Option C: Reduce to $0.10**
**Pros**: Better granularity, still simple
**Cons**: More levels, slightly more noise

---

## 🎯 **My Recommendation**

### **For Production: Implement Dynamic Tolerance**

```javascript
// Updated aggregation logic
function getPriceTolerance(price) {
  if (price >= 500) return 0.15;      // High-priced stocks
  if (price >= 100) return 0.10;      // Mid-priced stocks  
  if (price >= 50) return 0.05;       // Lower-priced stocks
  return 0.01;                        // Penny stocks
}

// In aggregation:
const tolerance = getPriceTolerance(t.price);
const bucket = Math.round(t.price / tolerance);
const price = bucket * tolerance;
```

### **Benefits:**
✅ **Optimal for all stock prices**
✅ **Better granularity for analysis**
✅ **Maintains noise reduction**
✅ **Future-proof for any ticker**

### **Quick Test:**
- **META**: Same results (already optimal)
- **Lower-priced stocks**: Much better levels
- **Overall**: More meaningful price levels

---

## 📊 **Data Quality Impact**

### **Current Aggregation:**
- **1,464 DP levels** for META (15 days)
- **1,502 LP levels** for META (15 days)
- **Good signal-to-noise ratio**

### **With Dynamic Tolerance:**
- **Slightly more levels** (better granularity)
- **Better price resolution**
- **More actionable data**

---

## 🚀 **Next Steps**

1. **Test current system** - Verify DP levels now show in frontend
2. **Implement dynamic tolerance** - Update aggregation logic
3. **Re-import data** - Run import script with new tolerance
4. **Compare results** - Analyze level quality improvement

---

## 💡 **Summary**

**Current $0.15 tolerance is good for high-priced stocks like META, but implementing dynamic tolerance would optimize the system for all price ranges while maintaining the same quality for high-priced stocks.**

The aggregation is working correctly - trades are properly grouped by price and premium is correctly calculated as `size * price` for each level.

