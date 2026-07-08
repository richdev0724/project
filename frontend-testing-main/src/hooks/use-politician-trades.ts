import {
  useQuery,
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'
import { politicianTradesService } from '@/api/politician-trades.service'

export const queryKeys = {
  allTrades: ['politician-trades', 'all'] as const,
  politicianTrades: (name: string) =>
    ['politician-trades', 'politician', name] as const,
  politicianPortfolio: (name: string) =>
    ['politician-trades', 'portfolio', name] as const,
}

/**
 * Hook to fetch all politician trades (loads all at once, use with caution)
 */
export function useAllPoliticianTrades() {
  return useQuery({
    queryKey: queryKeys.allTrades,
    queryFn: () => politicianTradesService.getAllTrades(),
    staleTime: 1000 * 60 * 60 * 6, // 6 hours
    gcTime: 1000 * 60 * 60 * 24, // 24 hours
  })
}

/**
 * Hook to fetch paginated politician trades with infinite scroll
 */
export function usePaginatedPoliticianTrades(limit: number = 100) {
  return useInfiniteQuery({
    queryKey: ['politician-trades', 'paginated', limit],
    queryFn: ({ pageParam = 0 }) =>
      politicianTradesService.getPaginatedTrades(limit, pageParam),
    getNextPageParam: (lastPage) =>
      lastPage.hasMore ? lastPage.offset + limit : undefined,
    initialPageParam: 0,
    staleTime: 1000 * 60 * 60 * 6, // 6 hours
    gcTime: 1000 * 60 * 60 * 24, // 24 hours
  })
}

/**
 * Hook to fetch trades for a specific politician
 */
export function usePoliticianTrades(politicianName: string | null) {
  return useQuery({
    queryKey: politicianName
      ? queryKeys.politicianTrades(politicianName)
      : ['politician-trades', 'none'],
    queryFn: () =>
      politicianName
        ? politicianTradesService.getPoliticianTrades(politicianName)
        : Promise.resolve([]),
    enabled: !!politicianName,
    staleTime: 1000 * 60 * 60 * 6, // 6 hours
    gcTime: 1000 * 60 * 60 * 24, // 24 hours
  })
}

/**
 * Hook to fetch portfolio data for a specific politician
 */
export function usePoliticianPortfolio(politicianName: string | null) {
  return useQuery({
    queryKey: politicianName
      ? queryKeys.politicianPortfolio(politicianName)
      : ['politician-portfolio', 'none'],
    queryFn: () =>
      politicianName
        ? politicianTradesService.getPoliticianPortfolio(politicianName)
        : Promise.resolve(null),
    enabled: !!politicianName,
    staleTime: 1000 * 60 * 60 * 6, // 6 hours
    gcTime: 1000 * 60 * 60 * 24, // 24 hours
  })
}

/**
 * Hook to clear the trades cache
 */
export function useClearPoliticianTradesCache() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => politicianTradesService.clearCache(),
    onSuccess: () => {
      // Invalidate all politician trades queries
      queryClient.invalidateQueries({ queryKey: ['politician-trades'] })
    },
  })
}
