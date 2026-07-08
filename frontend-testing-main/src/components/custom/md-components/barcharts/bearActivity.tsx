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

type BearActivityChartProps = {
  tickers: Array<{
    symbol: string
    bearish_volume: number
  }>
}

export function BearActivityChart({ tickers }: BearActivityChartProps) {
  const formatNumber = (value: number) => {
    if (value >= 1000000) {
      return Math.floor(value / 1000000).toString() + 'M'
    } else if (value >= 1000) {
      return Math.floor(value / 1000).toString() + 'k'
    }
    return value.toString()
  }

  const sortedTickers =
    tickers?.sort((a, b) => b.bearish_volume - a.bearish_volume) || []
  const bearishVolumes = sortedTickers.map((ticker) => ticker.bearish_volume)
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
    colors: ['rgba(244, 63, 94, 0.3)'],
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
        series={[{ name: 'Volume', data: bearishVolumes }]}
        type='bar'
        height='100%'
      />
    </div>
  )
}

export default function BearActivity() {
  const today = new Date()
  const endDate = today.toISOString().split('T')[0]
  today.setMonth(today.getMonth() - 2)
  const startDate = today.toISOString().split('T')[0]

  const { data: contractsTopActivity } = useContractsTopActivityQuery({
    startDate,
    endDate,
  })

  return <BearActivityChart tickers={contractsTopActivity?.sold ?? []} />
}
