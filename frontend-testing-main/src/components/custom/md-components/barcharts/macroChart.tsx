import { RecentMacro } from '@/models/market-data-analysis.model'
import { ApexOptions } from 'apexcharts'
import { useEffect, useState } from 'react'
import ReactApexChart from 'react-apexcharts'

interface MacroChartProps {
  data: RecentMacro[] | null
  isLoading?: boolean
}

export default function MacroChart({
  data,
  isLoading = false,
}: MacroChartProps) {
  const [chartData, setChartData] = useState<{
    series: ApexAxisChartSeries
    options: ApexOptions
  } | null>(null)

  useEffect(() => {
    if (!data || data.length === 0) {
      setChartData(null)
      return
    }

    // Process data
    const data1 = data.map((d: RecentMacro) =>
      parseFloat(d.percent_call_opening)
    )
    const data2 = data.map((d: RecentMacro) =>
      parseFloat(d.percent_put_opening)
    )
    const dates = data.map((d: RecentMacro) => {
      const [year, month, day] = d.date.split('-')
      return `${parseInt(month, 10)}/${parseInt(day, 10)}/${year.slice(-2)}`
    })

    const options: ApexOptions = {
      chart: {
        height: 320,
        type: 'line',
        toolbar: { show: false },
        zoom: { enabled: false, type: 'x', autoScaleYaxis: false },
      },
      stroke: {
        curve: 'smooth',
        width: 3,
      },
      colors: ['#06b6d4', 'rgba(244, 63, 94, 0.7)'],
      fill: {
        type: ['solid', 'solid'],
        opacity: [0.35, 1],
      },
      grid: {
        borderColor: 'rgba(255, 255, 255, 0.3)',
      },
      xaxis: {
        categories: dates,
        labels: {
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
            text: 'Calls',
            style: { color: '#10b981' },
          },
          labels: {
            style: { colors: ['#10b981'] },
          },
        },
        {
          opposite: true,
          title: {
            text: 'Puts',
            style: { color: 'rgba(244, 63, 94, 0.7)' },
          },
          labels: {
            style: { colors: ['rgba(244, 63, 94, 0.7)'] },
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
    }

    const series = [
      {
        name: 'Calls',
        type: 'area',
        data: data1,
      },
      {
        name: 'Puts',
        type: 'line',
        data: data2,
      },
    ]

    setChartData({ series, options })
  }, [data])

  // Render loading state
  if (isLoading) {
    return (
      <div className='flex h-80 w-full items-center justify-center'>
        <div className='h-10 w-10 animate-spin rounded-full border-b-2 border-t-2 border-cyan-500'></div>
      </div>
    )
  }

  // Render no data state
  if (!chartData) {
    return (
      <div className='flex h-80 w-full items-center justify-center text-gray-500'>
        No data available
      </div>
    )
  }

  // Render chart
  return (
    <ReactApexChart
      height={320}
      options={chartData.options}
      series={chartData.series}
      type='line'
    />
  )
}
