import { hotContractService } from '@/api/hot-contracts.service'
import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

export type RecentOpeningData = {
  strike: string
  expiration: string
  bought: number
  sold: number
  type: 'call' | 'put'
}

export const useRecentOpening = (symbol: any) => {
  const query = useQuery({
    queryKey: ['marketDataAnalysis', 'recentOpening'],
    queryFn: () => hotContractService.getRecentOpening(symbol),
  })

  // Number formatting function
  const formatNumber = (num: number) => {
    if (num >= 1_000_000) {
      return `${(num / 1_000_000).toFixed(1)}M`
    } else if (num >= 1_000) {
      return `${(num / 1_000).toFixed(1)}k`
    }
    return num?.toString() || 'N/A'
  }

  // Date formatting function to convert YYYY-MM-DD to MM/DD/YY
  const formatDate = (date: any) => {
    if (!date) return 'N/A'
    const [year, month, day] = date.split('-') ?? []
    if (year && month && day) {
      return `${parseInt(month, 10)}/${parseInt(day, 10)}/${year.slice(-2)}`
    }
    return 'N/A'
  }

  const opData = useMemo(
    () => ({
      data:
        query.data?.data?.map(
          (item: {
            strike: any
            expiration: any
            daily_bought_volume: any
            daily_sold_volume: any
            type: any
          }) => ({
            strike: item.strike,
            expiration: formatDate(item?.expiration),
            bought: formatNumber(item.daily_bought_volume),
            sold: formatNumber(item.daily_sold_volume),
            type: item.type,
          })
        ) ?? [],
    }),
    [query.data]
  )

  return {
    ...query,
    opData,
  }
}
