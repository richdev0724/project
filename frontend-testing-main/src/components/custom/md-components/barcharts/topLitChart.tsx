import { ApexOptions } from 'apexcharts'
import Chart from 'react-apexcharts'
import { getLitPoolTopActivity } from '../../../../api/market-data'
import { useQuery } from '@tanstack/react-query'

type LitPoolTopActivityProps = {
  tickers: Array<{
    symbol?: string
    lit_volume?: number
  }>
}

export const LitPoolChart = ({ tickers }: LitPoolTopActivityProps) => {
  const formatNumber = (value: number) => {
    if (value >= 1000000) {
      const formatted = (value / 1000000).toFixed(1)
      return formatted.endsWith('.0')
        ? formatted.slice(0, -2) + 'M'
        : formatted + 'M'
    } else if (value >= 1000) {
      const formatted = (value / 1000).toFixed(1)
      return formatted.endsWith('.0')
        ? formatted.slice(0, -2) + 'k'
        : formatted + 'k'
    }
    return value.toString()
  }

  const litVolumes =
    tickers?.map((ticker) => ticker?.lit_volume ?? 0).slice(0, 8) || []
  const litSymbols = tickers?.map((ticker) => ticker?.symbol).slice(0, 8) || []

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
    colors: ['#0d9488'],
    grid: {
      show: true,
      borderColor: 'rgba(255, 255, 255, 0.3)',
    },
    dataLabels: {
      enabled: false,
    },
    xaxis: {
      categories: litSymbols,
      labels: {
        show: true,
        style: {
          colors: '#0d9488',
          fontSize: '11px',
          fontWeight: 600,
          cssClass: 'apexcharts-xaxis-label',
        },
        formatter: (value: string) => formatNumber(parseFloat(value)),
      },
    },
    yaxis: {
      labels: {
        show: true,
        style: {
          colors: '#0d9488',
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

  const height = `${Math.max(250, Math.min(500, litSymbols.length * 50))}px`

  return (
    <div style={{ height: height, width: '100%' }}>
      <Chart
        options={options}
        series={[{ name: 'Lit Volume', data: litVolumes }]}
        type='bar'
        height='100%'
      />
    </div>
  )
}

const useLitPoolTopActivityQuery = () =>
  useQuery({
    queryKey: ['marketDataAnalysis', 'litPoolTopActivity'],
    queryFn: async () => {
      const { data } = await getLitPoolTopActivity()
      return data
    },
  })

export default function LitPoolTopActivity() {
  const { data: litPoolTopActivity } = useLitPoolTopActivityQuery()
  return <LitPoolChart tickers={litPoolTopActivity || []} />
}
