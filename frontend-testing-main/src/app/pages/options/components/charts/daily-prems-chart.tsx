import { ApexOptions } from 'apexcharts'
import { useEffect, useState } from 'react'
import ReactApexChart from 'react-apexcharts'

export default function DailyPremsChart({ data }: any) {
  const [chartData, setChartData] = useState<any[]>([])

  const formatNumber = (number: number) => {
    const num = Number(number)

    if (isNaN(num)) return '0'

    if (num >= 1000000000) {
      return (num / 1000000000).toFixed(2).replace(/\.00$/, '') + 'B'
    } else if (num >= 1000000) {
      return (num / 1000000).toFixed(2).replace(/\.00$/, '') + 'M'
    } else if (num >= 1000) {
      return (num / 1000).toFixed(2).replace(/\.00$/, '') + 'K'
    }
    return num.toFixed(2).replace(/\.00$/, '')
  }

  useEffect(() => {
    if (data && data.length > 0) {
      setChartData(data)
    }
  }, [data])

  const data1 = chartData?.map((item: any) => item?.call_premium ?? 0) || []
  const data2 = chartData?.map((item: any) => item?.put_premium ?? 0) || []
  const data3 = chartData?.map((item: any) => item?.total_premium ?? 0) || []
  const dates =
    chartData?.map((item: any) => {
      if (!item?.date) return 'N/A'
      const [year, month, day] = item.date.split('-') ?? []
      return `${parseInt(month, 10)}-${parseInt(day, 10)}-${year.slice(2)}`
    }) || []

  const options: ApexOptions = {
    series: [
      {
        name: 'Calls',
        type: 'area',
        data: data1,
      },
      {
        name: 'Puts',
        type: 'area',
        data: data2,
      },
      {
        name: 'Total Premium',
        type: 'line',
        data: data3,
      },
    ],

    chart: {
      height: 320,
      width: '100%',
      type: 'line',
      toolbar: {
        show: false,
      },
      zoom: {
        enabled: false,
      },
      brush: {
        enabled: false,
      },
      events: {
        mounted: (chart: { windowResizeHandler: () => void }) => {
          chart.windowResizeHandler()
        },
      },
    },
    stroke: {
      curve: 'smooth',
      width: 2,
    },
    colors: ['#06b6d4', 'rgba(244, 63, 94, 0.3)', '#eab308'],
    grid: {
      borderColor: '#f2f5f7',
      show: true,
    },
    fill: {
      type: 'solid',
      opacity: [0.35, 0.35, 1],
    },
    labels: dates,
    markers: {
      size: 0,
    },
    xaxis: {
      type: 'category',
      labels: {
        show: true,
        rotate: -45,
        style: {
          colors: '#8c9097',
          fontSize: '11px',
          fontWeight: 600,
        },
      },
    },
    yaxis: [
      {
        title: {
          text: 'Premium (millions)',
          style: {
            color: '#10b981',
          },
        },
        labels: {
          show: true,
          style: {
            colors: '#10b981',
            fontSize: '11px',
            fontWeight: 600,
          },
          formatter: (value: any) => formatNumber(value),
        },
      },
    ],
    tooltip: {
      theme: 'dark',
      fillSeriesColor: false,
      style: {
        fontSize: '12px',
        fontFamily: undefined,
      },
    },
    noData: {
      text: 'No data available',
      align: 'center',
      verticalAlign: 'middle',
      style: {
        color: '#888',
        fontSize: '14px',
      },
    },
  }

  if (!chartData || chartData.length === 0) {
    return (
      <div className='py-10 text-center text-gray-500'>
        No chart data available
      </div>
    )
  }

  return (
    <ReactApexChart
      height={320}
      width='100%'
      options={options}
      series={options.series}
      type='line'
    />
  )
}
