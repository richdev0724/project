import { companyProfileService } from './company-profile.service'

// GICS (Global Industry Classification Standard) 11 Sectors
export const GICS_SECTORS = [
  'Communication Services',
  'Consumer Discretionary',
  'Consumer Staples',
  'Energy',
  'Financials',
  'Health Care',
  'Industrials',
  'Information Technology',
  'Materials',
  'Real Estate',
  'Utilities',
]

// Cache for ticker to sector mapping
const tickerSectorCache = new Map<string, string>()

export class SectorMappingService {
  private static instance: SectorMappingService

  public static getInstance(): SectorMappingService {
    if (!SectorMappingService.instance) {
      SectorMappingService.instance = new SectorMappingService()
    }
    return SectorMappingService.instance
  }

  async getSectorForTicker(ticker: string): Promise<string | null> {
    // Check cache first
    if (tickerSectorCache.has(ticker)) {
      return tickerSectorCache.get(ticker) || null
    }

    try {
      const profile = await companyProfileService.getCompanyProfile(ticker)
      if (profile?.sector) {
        const sector = this.normalizeSectorName(profile.sector)
        tickerSectorCache.set(ticker, sector)
        return sector
      }
    } catch (error) {
      // Silently fail
    }

    return null
  }

  async getSectorsForTickers(tickers: string[]): Promise<Map<string, string>> {
    const sectorMap = new Map<string, string>()

    // Process in batches to avoid overwhelming the API
    const batchSize = 10
    for (let i = 0; i < tickers.length; i += batchSize) {
      const batch = tickers.slice(i, i + batchSize)
      const promises = batch.map(async (ticker) => {
        const sector = await this.getSectorForTicker(ticker)
        if (sector) {
          sectorMap.set(ticker, sector)
        }
      })

      await Promise.allSettled(promises)

      // Small delay between batches
      if (i + batchSize < tickers.length) {
        await new Promise((resolve) => setTimeout(resolve, 100))
      }
    }

    return sectorMap
  }

  private normalizeSectorName(sector: string): string {
    // Normalize sector names to match GICS sectors
    const normalized = sector.trim()

    // Map common variations to GICS sectors
    const sectorMappings: { [key: string]: string } = {
      technology: 'Information Technology',
      tech: 'Information Technology',
      it: 'Information Technology',
      software: 'Information Technology',
      hardware: 'Information Technology',
      semiconductors: 'Information Technology',
      telecommunications: 'Communication Services',
      telecom: 'Communication Services',
      media: 'Communication Services',
      entertainment: 'Communication Services',
      healthcare: 'Health Care',
      'health care': 'Health Care',
      medical: 'Health Care',
      pharmaceuticals: 'Health Care',
      biotech: 'Health Care',
      financial: 'Financials',
      finance: 'Financials',
      banking: 'Financials',
      insurance: 'Financials',
      consumer: 'Consumer Discretionary',
      retail: 'Consumer Discretionary',
      automotive: 'Consumer Discretionary',
      'consumer staples': 'Consumer Staples',
      staples: 'Consumer Staples',
      food: 'Consumer Staples',
      beverages: 'Consumer Staples',
      oil: 'Energy',
      gas: 'Energy',
      utilities: 'Utilities',
      utility: 'Utilities',
      industrial: 'Industrials',
      manufacturing: 'Industrials',
      aerospace: 'Industrials',
      defense: 'Industrials',
      materials: 'Materials',
      chemicals: 'Materials',
      mining: 'Materials',
      metals: 'Materials',
      'real estate': 'Real Estate',
      reit: 'Real Estate',
      reits: 'Real Estate',
    }

    // Check for exact match first
    const exactMatch = GICS_SECTORS.find(
      (s) => s.toLowerCase() === normalized.toLowerCase()
    )

    if (exactMatch) {
      return exactMatch
    }

    // Check for partial matches
    const partialMatch = GICS_SECTORS.find(
      (s) =>
        normalized.toLowerCase().includes(s.toLowerCase()) ||
        s.toLowerCase().includes(normalized.toLowerCase())
    )

    if (partialMatch) {
      return partialMatch
    }

    // Check mappings
    const mappedSector = sectorMappings[normalized.toLowerCase()]
    if (mappedSector) {
      return mappedSector
    }

    // If no match found, return the original sector
    return normalized
  }

  getAvailableSectors(): string[] {
    return GICS_SECTORS
  }

  clearCache(): void {
    tickerSectorCache.clear()
  }
}

export const sectorMappingService = SectorMappingService.getInstance()
