import { ApexOptions } from 'apexcharts'
import Chart from 'react-apexcharts'
import { marketDataAnalysisService } from '@/api/market-data.service'
import { useQuery } from '@tanstack/react-query'

type TopContractsActivityParams = {
  startDate: string
  endDate: string
}

const useContractsTopActivityQuery = (params: TopContractsActivityParams) =>
  useQuery({
    queryKey: ['marketDataAnalysis', 'contractsTopActivity'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getContractsTopActivity(params)
      return data
    },
  })

type BullActivityChartProps = {
  tickers: Array<{
    symbol: string
    bullish_volume: number
  }>
}

export function BullActivityChart({ tickers }: BullActivityChartProps) {
  const formatNumber = (value: number) => {
    if (value >= 1000000) {
      return Math.floor(value / 1000000).toString() + 'M'
    } else if (value >= 1000) {
      return Math.floor(value / 1000).toString() + 'k'
    }
    return value.toString()
  }

  const sortedTickers = tickers.sort(
    (a, b) => b.bullish_volume - a.bullish_volume
  )
  const bullishVolumes = sortedTickers.map((ticker) => ticker.bullish_volume)
  const tickerSymbols = sortedTickers.map((ticker) => ticker.symbol)

  const options: ApexOptions = {
    chart: {
      toolbar: {
        show: false,
      },
      events: {
        mounted: (chart) => {
          chart.windowResizeHandler()
        },
      },
    },
    plotOptions: {
      bar: {
        borderRadius: 4,
        horizontal: true,
        barHeight: '80%',
        columnWidth: '60%',
      },
    },
    colors: ['#10b981'],
    grid: {
      borderColor: 'rgba(255, 255, 255, 0.3)',
    },
    dataLabels: {
      enabled: false,
    },
    xaxis: {
      categories: tickerSymbols,
      labels: {
        show: true,
        style: {
          colors: '#8c9097',
          fontSize: '11px',
          fontWeight: 600,
          cssClass: 'apexcharts-xaxis-label',
        },
        formatter: (value: string) => value,
      },
    },
    yaxis: {
      labels: {
        show: true,
        style: {
          colors: '#8c9097',
          fontSize: '11px',
          fontWeight: 600,
          cssClass: 'apexcharts-yaxis-label',
        },
      },
    },
    tooltip: {
      y: {
        formatter: (value: number) => formatNumber(value),
      },
    },
  }

  const height = `${Math.max(250, Math.min(600, tickers.length * 40))}px`

  return (
    <div style={{ height: height, width: '100%' }}>
      <Chart
        options={options}
        series={[{ name: 'Volume', data: bullishVolumes }]}
        type='bar'
        height='100%'
      />
    </div>
  )
}

export default function BullActivity() {
  const today = new Date()
  const endDate = today.toISOString().split('T')[0]
  today.setMonth(today.getMonth() - 2)
  const startDate = today.toISOString().split('T')[0]

  const { data: contractsTopActivity } = useContractsTopActivityQuery({
    startDate,
    endDate,
  })

  return <BullActivityChart tickers={contractsTopActivity?.bought ?? []} />
}
