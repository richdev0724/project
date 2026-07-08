import { apiService } from './api.service'

export interface AnalystRating {
  symbol: string
  date: string
  rating: string
  ratingScore: number
  ratingRecommendation: string
  ratingDetailsDCFScore: number
  ratingDetailsDCFRecommendation: string
  ratingDetailsROEScore: number
  ratingDetailsROERecommendation: string
  ratingDetailsROAScore: number
  ratingDetailsROARecommendation: string
  ratingDetailsDEScore: number
  ratingDetailsDERecommendation: string
  ratingDetailsPEScore: number
  ratingDetailsPERecommendation: string
  ratingDetailsPBScore: number
  ratingDetailsPBRecommendation: string
}

export interface PriceTarget {
  symbol: string
  publishedDate: string
  newsURL: string
  newsTitle: string
  analystName: string
  priceTarget: number
  adjPriceTarget: number
  priceWhenPosted: number
  newsPublisher: string
  newsBaseURL: string
  analystCompany: string
}

export interface UpgradeDowngrade {
  symbol: string
  publishedDate: string
  newsURL: string
  newsTitle: string
  newsPublisher: string
  newGrade: string
  previousGrade: string
  gradingCompany: string
  action: string
  priceWhenPosted: number
}

class AnalystRatingsService {
  async getAnalystRatings(ticker: string): Promise<AnalystRating[]> {
    const response = await apiService.get<AnalystRating[]>(
      `/analyst-ratings/${ticker}`
    )
    return response
  }

  async getPriceTarget(ticker: string): Promise<PriceTarget[]> {
    const response = await apiService.get<PriceTarget[]>(
      `/analyst-ratings/${ticker}/price-target`
    )
    return response
  }

  async getUpgradesDowngrades(ticker: string): Promise<UpgradeDowngrade[]> {
    const response = await apiService.get<UpgradeDowngrade[]>(
      `/analyst-ratings/${ticker}/upgrades-downgrades`
    )
    return response
  }
}

export const analystRatingsService = new AnalystRatingsService()
