import { ApexOptions } from 'apexcharts'
import Chart from 'react-apexcharts'
import { useQuery } from '@tanstack/react-query'
import { marketDataAnalysisService } from '@/api/market-data.service'
// import { formatNumber } from '@/utils'

const useDarkPoolTopActivityQuery = () =>
  useQuery({
    queryKey: ['marketDataAnalysis', 'darkPoolTopActivity'],
    queryFn: async () => {
      const { data } = await marketDataAnalysisService.getDarkPoolTopActivity()
      return data
    },
  })

type Props = {
  data: Array<{
    symbol: string
    dark_volume: number
  }>
}

function DarkPoolChart({ data }: Props) {
  const formatNumberLabel = (value: number) => {
    if (value >= 1000000) {
      return Math.floor(value / 1000000) + 'M'
    } else if (value >= 1000) {
      return Math.floor(value / 1000) + 'k'
    }
    return value.toString()
  }

  const darkPoolVolumes = data?.map((item) => item.dark_volume) || []
  const darkPoolSymbols = data?.map((item) => item.symbol) || []

  const options: ApexOptions = {
    chart: {
      height: 320,
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
      },
    },
    colors: ['#845adf'],
    grid: {
      borderColor: '#8c9097',
    },
    dataLabels: {
      enabled: false,
    },
    xaxis: {
      categories: darkPoolSymbols,
      labels: {
        show: true,
        style: {
          colors: '#8c9097',
          fontSize: '11px',
          fontWeight: 200,
          cssClass: 'apexcharts-xaxis-label',
        },
        formatter: (value: string) => {
          const index = darkPoolSymbols.indexOf(value)
          return formatNumberLabel(darkPoolVolumes[index])
        },
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
      theme: 'dark',
      fillSeriesColor: false,
      style: {
        fontSize: '12px',
        fontFamily: undefined,
      },
      y: {
        formatter: (value: number) => formatNumberLabel(value),
      },
      x: {
        formatter: (val: number) => darkPoolSymbols[val] || '',
      },
    },
  }

  return (
    <Chart
      height={'100%'}
      options={options}
      series={[{ name: 'Volume', data: darkPoolVolumes }]}
      type='bar'
    />
  )
}

export function DarkPool() {
  const { data } = useDarkPoolTopActivityQuery()
  return <DarkPoolChart data={data || []} />
}
