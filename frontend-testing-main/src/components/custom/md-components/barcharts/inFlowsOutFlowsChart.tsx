import { RecentMacro } from '@/models/market-data-analysis.model'
import ReactApexChart from 'react-apexcharts'
import { ApexOptions } from 'apexcharts'

interface InFlowsOutFlowsChartProps {
  data: RecentMacro[]
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

  const callsBought = data.map((data: RecentMacro) => data.calls_bought_premium)
  const callsSold = data.map((data: RecentMacro) => data.calls_sold_premium)
  const putsBought = data.map((data: RecentMacro) => data.puts_bought_premium)
  const putsSold = data.map((data: RecentMacro) => data.puts_sold_premium)

  const dates = data.map((data: RecentMacro) => {
    const [year, month, day] = data.date.split('-')
    return `${month}-${day}-${year}`
  })

  const inflows = callsBought.map(
    (val: string, index: number) => val + putsSold[index]
  )
  const outflows = callsSold.map(
    (val: string, index: number) => val + putsBought[index]
  )

  const options: ApexOptions = {
    series: [
      {
        name: 'Inflows',
        data: inflows.map((val: string) => parseFloat(val)),
      },
      {
        name: 'Outflows',
        data: outflows.map((val: string) => parseFloat(val)),
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
        columnWidth: '40%',
      },
    },
    grid: {
      borderColor: 'rgba(255, 255, 255, 0.3)',
    },
    dataLabels: {
      enabled: false,
    },
    colors: ['#06b6d4', 'rgba(244, 63, 94, 1)'],
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
