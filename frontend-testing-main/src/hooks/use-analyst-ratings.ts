import { useQuery } from '@tanstack/react-query'
import {
  analystRatingsService,
  AnalystRating,
  PriceTarget,
  UpgradeDowngrade,
} from '@/api/analyst-ratings.service'

export const useAnalystRatings = (ticker: string) => {
  const { data, isLoading, error } = useQuery<AnalystRating[], Error>({
    queryKey: ['analyst-ratings', ticker],
    queryFn: () => analystRatingsService.getAnalystRatings(ticker),
    staleTime: 1000 * 60 * 60, // 1 hour - ratings don't change frequently
    enabled: !!ticker,
  })

  return {
    rating: data?.[0], // Most recent rating
    ratings: data,
    isLoading,
    error,
  }
}

export const usePriceTarget = (ticker: string) => {
  const { data, isLoading, error } = useQuery<PriceTarget[], Error>({
    queryKey: ['price-target', ticker],
    queryFn: () => analystRatingsService.getPriceTarget(ticker),
    staleTime: 1000 * 60 * 60, // 1 hour
    enabled: !!ticker,
  })

  return {
    priceTargets: data,
    isLoading,
    error,
  }
}

export const useUpgradesDowngrades = (ticker: string, limit: number = 8) => {
  const { data, isLoading, error } = useQuery<UpgradeDowngrade[], Error>({
    queryKey: ['upgrades-downgrades', ticker, limit],
    queryFn: () => analystRatingsService.getUpgradesDowngrades(ticker),
    staleTime: 1000 * 60 * 60, // 1 hour
    enabled: !!ticker,
    select: (data) => data?.slice(0, limit), // Limit results
  })

  return {
    upgradesDowngrades: data,
    isLoading,
    error,
  }
}
