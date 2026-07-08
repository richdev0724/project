const fs = require('fs');
const path = require('path');
const axios = require('axios');

const CACHE_DIR = path.join(__dirname, '../../cache/logos');
const CACHE_EXPIRY = 30 * 24 * 60 * 60 * 1000; // 30 days

// Ensure cache directory exists
if (!fs.existsSync(CACHE_DIR)) {
  fs.mkdirSync(CACHE_DIR, { recursive: true });
}

class LogoCacheService {
  constructor() {
    this.cache = new Map();
  }

  getCachePath(ticker) {
    return path.join(CACHE_DIR, `${ticker.toUpperCase()}.png`);
  }

  getCachedLogo(ticker) {
    const cachePath = this.getCachePath(ticker);
    
    if (fs.existsSync(cachePath)) {
      const stats = fs.statSync(cachePath);
      const now = Date.now();
      
      // Check if cache is expired
      if (now - stats.mtime.getTime() < CACHE_EXPIRY) {
        return `/api/logo-file/${ticker}`;
      } else {
        // Remove expired cache
        fs.unlinkSync(cachePath);
      }
    }
    
    return null;
  }

  async setCachedLogo(ticker, imageBuffer) {
    const cachePath = this.getCachePath(ticker);
    fs.writeFileSync(cachePath, imageBuffer);
    return `/api/logo-file/${ticker}`;
  }

  async fetchLogoFromPolygon(ticker) {
    try {
      const response = await axios.get(
        `https://api.polygon.io/v1/meta/symbols/${ticker}/company`,
        { 
          params: { apikey: process.env.POLYGON_API_KEY },
          timeout: 10000
        }
      );
      
      if (response.status === 200 && response.data && response.data.logo) {
        // Fetch the actual logo image from the URL provided
        const logoResponse = await axios.get(response.data.logo, {
          responseType: 'arraybuffer',
          timeout: 10000
        });
        
        if (logoResponse.status === 200 && logoResponse.data.length > 0) {
          return logoResponse.data;
        }
      }
      return null;
    } catch (error) {
      console.log(`Polygon logo fetch failed for ${ticker}:`, error.message);
      return null;
    }
  }

  async fetchLogoFromYahoo(ticker) {
    try {
      const response = await axios.get(
        `https://logo.clearbit.com/${ticker.toLowerCase()}.com`,
        { responseType: 'arraybuffer', timeout: 10000 }
      );
      
      if (response.status === 200 && response.data.length > 0) {
        return response.data;
      }
      return null;
    } catch (error) {
      console.log(`Yahoo/Clearbit logo fetch failed for ${ticker}:`, error.message);
      return null;
    }
  }

  async fetchLogoFromSquareSpace(ticker) {
    try {
      // Try different domain variations for square logos
      const domains = [
        `${ticker.toLowerCase()}.com`,
        `www.${ticker.toLowerCase()}.com`,
        `${ticker.toLowerCase()}.io`,
        `${ticker.toLowerCase()}.co`
      ];
      
      for (const domain of domains) {
        try {
          const response = await axios.get(
            `https://api.squarespace.com/1.0/site/${domain}/logo`,
            { responseType: 'arraybuffer', timeout: 5000 }
          );
          
          if (response.status === 200 && response.data.length > 0) {
            return response.data;
          }
        } catch (e) {
          // Try next domain
        }
      }
      return null;
    } catch (error) {
      console.log(`SquareSpace logo fetch failed for ${ticker}:`, error.message);
      return null;
    }
  }

  async fetchLogoFromFMP(ticker) {
    try {
      const FMP_API_KEY = process.env.FMP_API_KEY;
      if (!FMP_API_KEY) {
        console.log('FMP_API_KEY not configured, skipping FMP logo');
        return null;
      }

      const response = await axios.get(
        `https://financialmodelingprep.com/api/v3/profile/${ticker.toUpperCase()}?apikey=${FMP_API_KEY}`,
        { timeout: 10000 }
      );
      
      if (response.data && response.data.length > 0 && response.data[0].image) {
        const logoUrl = response.data[0].image;
        
        // Fetch the actual image
        const imageResponse = await axios.get(logoUrl, {
          responseType: 'arraybuffer',
          timeout: 10000,
          headers: {
            'User-Agent': 'Mozilla/5.0 (compatible; LogoService/1.0)'
          }
        });
        
        if (imageResponse.status === 200 && imageResponse.data.length > 0) {
          return imageResponse.data;
        }
      }
      return null;
    } catch (error) {
      console.log(`FMP logo fetch failed for ${ticker}:`, error.message);
      return null;
    }
  }

  async fetchLogoFromTradingView(ticker) {
    try {
      const response = await axios.get(
        `https://s3-symbol-logo.tradingview.com/${ticker.toLowerCase()}.svg`,
        { 
          responseType: 'arraybuffer', 
          timeout: 10000,
          headers: {
            'User-Agent': 'Mozilla/5.0 (compatible; LogoService/1.0)',
            'Accept': 'image/svg+xml,image/*,*/*'
          }
        }
      );
      
      if (response.status === 200 && response.data.length > 0) {
        return response.data;
      }
      return null;
    } catch (error) {
      console.log(`TradingView logo fetch failed for ${ticker}:`, error.message);
      return null;
    }
  }

  async fetchLogoFromBrandfetch(ticker) {
    try {
      const response = await axios.get(
        `https://cdn.brandfetch.io/${ticker}?c=1id0O0gYpigsoWDSe8b`,
        { 
          responseType: 'arraybuffer', 
          timeout: 10000,
          headers: {
            'User-Agent': 'Mozilla/5.0 (compatible; LogoService/1.0)',
            'Accept': 'image/webp,image/*,*/*',
            'Referer': 'https://your-app.com'
          },
          maxRedirects: 5
        }
      );
      
      if (response.status === 200 && response.data.length > 0) {
        return response.data;
      }
      return null;
    } catch (error) {
      console.log(`Brandfetch logo fetch failed for ${ticker}:`, error.message);
      return null;
    }
  }


  async getLogo(ticker) {
    if (!ticker) return null;

    // Check cache first
    const cachedUrl = this.getCachedLogo(ticker);
    if (cachedUrl) {
      return cachedUrl;
    }

    // Try multiple sources in order of preference (FMP first - working)
    let imageBuffer = null;
    
    // Try FMP first (working, usually square)
    imageBuffer = await this.fetchLogoFromFMP(ticker);
    
    // If FMP fails, try Polygon
    if (!imageBuffer) {
      imageBuffer = await this.fetchLogoFromPolygon(ticker);
    }
    
    // If Polygon fails, try Clearbit as last resort (may not be square)
    if (!imageBuffer) {
      imageBuffer = await this.fetchLogoFromYahoo(ticker);
    }

    // If we got an image, cache it
    if (imageBuffer) {
      return await this.setCachedLogo(ticker, imageBuffer);
    }

    return null;
  }

  getCacheStats() {
    const files = fs.readdirSync(CACHE_DIR);
    const stats = {
      totalLogos: files.length,
      totalSize: 0,
      oldestFile: null,
      newestFile: null
    };

    let oldestTime = Date.now();
    let newestTime = 0;

    files.forEach(file => {
      const filePath = path.join(CACHE_DIR, file);
      const fileStats = fs.statSync(filePath);
      
      stats.totalSize += fileStats.size;
      
      if (fileStats.mtime.getTime() < oldestTime) {
        oldestTime = fileStats.mtime.getTime();
        stats.oldestFile = file;
      }
      
      if (fileStats.mtime.getTime() > newestTime) {
        newestTime = fileStats.mtime.getTime();
        stats.newestFile = file;
      }
    });

    stats.totalSizeMB = (stats.totalSize / (1024 * 1024)).toFixed(2);
    return stats;
  }

  clearExpiredLogos() {
    const files = fs.readdirSync(CACHE_DIR);
    const now = Date.now();
    let clearedCount = 0;

    files.forEach(file => {
      const filePath = path.join(CACHE_DIR, file);
      const stats = fs.statSync(filePath);
      
      if (now - stats.mtime.getTime() > CACHE_EXPIRY) {
        fs.unlinkSync(filePath);
        clearedCount++;
      }
    });

    return clearedCount;
  }
}

module.exports = new LogoCacheService();