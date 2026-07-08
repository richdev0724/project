import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

export const useTopSweepsOrders = (payload: any) => {
  const query = useQuery({
    queryKey: ['market', 'top-sweeps'],
    queryFn: async () => {
      const { data } = await marketDataAnalysisService.getTopSweeps(payload)
      return data
    },
    enabled: !!payload,
  })

  const formatValue = (value: string) => {
    const numericValue = parseFloat(value)
    return numericValue % 1 === 0
      ? numericValue.toString()
      : numericValue.toFixed(2)
  }

  // Specific number formatting function for large numbers
  const formatLargeNumber = (num: number) => {
    if (num >= 1000000) {
      return (num / 1000000).toFixed(1) + 'M'
    } else if (num >= 1000) {
      return (num / 1000).toFixed(1) + 'k'
    }
    return num.toString()
  }

  const sweepData = {
    sweeps:
      query.data?.map((item: any) => {
        const datePart = item?.sip_timestamp_friendly?.split(' ')[0]
        let formattedDate = 'N/A'
        if (datePart) {
          const [year, month, day] = datePart.split('-') ?? []
          formattedDate = `${parseInt(month)}/${parseInt(day)}/${year.slice(
            -2
          )}`
        }

        let formattedExpiration = 'N/A'
        if (item.expiration) {
          const [expYear, expMonth, expDay] = item.expiration.split('-') ?? []
          formattedExpiration = `${parseInt(expMonth)}/${parseInt(
            expDay
          )}/${expYear.slice(-2)}`
        }

        const typeShort = item.type === 'Call' ? 'c' : 'p'

        return {
          symbol: item.ticker,
          contract:
            formatValue(item.strike) + typeShort + ' ' + formattedExpiration,
          price: item.price,
          volume: formatLargeNumber(item.size),
          premium: `${formatLargeNumber(item.net_premium)}`,
          date: formattedDate,
        }
      }) ?? [],
  }

  return {
    ...query,
    sweepData: useMemo(() => sweepData, [query.data]),
  }
}
