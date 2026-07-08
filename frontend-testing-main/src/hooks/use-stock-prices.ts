import { miscellaneousService } from '@/api'
import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

interface StockPricesPayload {
  symbol: string
  startDate?: string
  endDate?: string
  timeframe?: string
  interval?: string
}

export const useStockPrices = (payload: StockPricesPayload) => {
  const query = useQuery({
    queryKey: ['misc', 'stockPrices', payload],
    queryFn: async () => {
      const { data } = await miscellaneousService.getStockPrices(payload)
      return data
    },
    enabled: !!payload,
  })

  const sortedCandleData = useMemo(() => {
    return (query.data ?? []).sort(
      (
        a: { date: string | number | Date },
        b: { date: string | number | Date }
      ) => {
        return new Date(a.date).getTime() - new Date(b.date).getTime()
      }
    )
  }, [query.data])

  const chartData = {
    lineData: sortedCandleData.map((item: { time: any; c: any }) => ({
      time: item.time,
      value: item.c,
    })),
    candleData: sortedCandleData.map(
      (item: { date: any; o: any; h: any; l: any; c: any }) => ({
        time: item.date,
        open: item.o,
        high: item.h,
        low: item.l,
        close: item.c,
      })
    ),
    lineDataSmall: sortedCandleData.map((item: { unix: any; c: any }) => ({
      time: item.unix,
      value: item.c,
    })),
    candleDataSmall: sortedCandleData.map(
      (item: { unix: any; o: any; h: any; l: any; c: any }) => ({
        time: item.unix,
        open: item.o,
        high: item.h,
        low: item.l,
        close: item.c,
      })
    ),
    volumeData: sortedCandleData.map((item: { date: any; v: any }) => ({
      x: item.date,
      y: item.v,
    })),
  }

  return {
    ...query,
    chartData,
  }
}
