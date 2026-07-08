import { RecentMacroPerTickerRoot } from '@/models/market-data-analysis.model'
import { ApexOptions } from 'apexcharts'
import ReactApexChart from 'react-apexcharts'

interface InFlowsOutFlowsChartProps {
  data: RecentMacroPerTickerRoot[]
}

export default function InFlowsOutFlowsChart({
  data,
}: InFlowsOutFlowsChartProps) {
  const formatNumber = (num: number) => {
    if (num >= 1000000000) {
      const billions = (num / 1000000000).toFixed(1)
      return billions + 'B'
    } else if (num >= 1000000) {
      const millions = (num / 1000000).toFixed(1)
      return millions + 'M'
    }
    return num.toString()
  }

  const callsBought = data.map(
    (data: RecentMacroPerTickerRoot) => data.call_volume_purchased
  )
  const callsSold = data.map(
    (data: RecentMacroPerTickerRoot) => data.call_volume_sold
  )
  const putsBought = data.map(
    (data: RecentMacroPerTickerRoot) => data.put_volume_purchased
  )
  const putsSold = data.map(
    (data: RecentMacroPerTickerRoot) => data.put_volume_sold
  )

  const dates = data.map((data: RecentMacroPerTickerRoot) => {
    const [year, month, day] = data.date.split('-')
    return `${month}-${day}-${year}`
  })

  const inflows = callsBought.map(
    (val: number, index: number) => val + putsSold[index]
  )
  const outflows = callsSold.map(
    (val: number, index: number) => val + putsBought[index]
  )

  const options: ApexOptions = {
    series: [
      {
        name: 'Inflows',
        data: inflows,
      },
      {
        name: 'Outflows',
        data: outflows,
      },
    ],
    chart: {
      height: 320,
      events: {
        mounted: (chart: any) => {
          chart.windowResizeHandler()
        },
      },
      toolbar: {
        show: false,
      },
    },
    plotOptions: {
      bar: {
        horizontal: false,
        columnWidth: '80%',
      },
    },
    grid: {
      borderColor: 'rgba(255, 255, 255, 0.3)',
    },
    dataLabels: {
      enabled: false,
    },
    colors: ['#0ea5e9', '#ef4444', '#dc2626'],
    stroke: {
      show: true,
      width: 2,
      colors: ['transparent'],
    },
    xaxis: {
      categories: dates,
      labels: {
        show: true,
        style: {
          colors: '#8c9097',
          fontSize: '11px',
          fontWeight: 600,
          cssClass: 'apexcharts-xaxis-label',
        },
      },
    },
    yaxis: {
      title: {
        text: '$ (billions)',
        style: {
          color: '#8c9097',
        },
      },
      labels: {
        show: true,
        style: {
          colors: 'rgba(255, 255, 255, 0.3)',
          fontSize: '11px',
          fontWeight: 600,
          cssClass: 'apexcharts-xaxis-label',
        },
        formatter: (value: number): string => formatNumber(value),
      },
    },
    fill: {
      opacity: 1,
    },
    tooltip: {
      theme: 'dark',
      fillSeriesColor: false,
      style: {
        fontSize: '12px',
        fontFamily: undefined,
      },
    },
  }

  return (
    <ReactApexChart
      type='bar'
      options={options}
      series={options.series}
      height={320}
    />
  )
}
