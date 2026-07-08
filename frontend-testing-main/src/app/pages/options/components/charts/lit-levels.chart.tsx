import React, { useState, useEffect } from 'react'
import ReactApexChart from 'react-apexcharts'
import { ApexOptions } from 'apexcharts'
import { useSymbolLevels } from '@/hooks/use-symbol-levels'
import Loader from '@/components/loader'

// TypeScript interface for the component props
interface LitLevelsProps {
  symbol: string
  height: number
}

// TypeScript interface for level data
interface LevelData {
  price: number
  net_premium: number
}

export const LitLevelsChart: React.FC<LitLevelsProps> = ({
  symbol,
  height,
}) => {
  // State to manage chart options and series
  const [chartOptions, setChartOptions] = useState<ApexOptions>({
    chart: {
      height: height,
      type: 'bar',
      toolbar: { show: false },
    },
    noData: { text: 'Loading...' },
  })
  const [series, setSeries] = useState<{ name: string; data: number[] }[]>([])

  // Calculate dates
  const endDate = new Date()
  const startDate = new Date()
  startDate.setDate(endDate.getDate() - 60)

  // Format dates to 'YYYY-MM-DD' format
  const formatDate = (date: Date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  const { data: symbolLevels, isLoading: levelsLoading } = useSymbolLevels({
    symbol: symbol,
    startDate: formatDate(startDate),
    endDate: formatDate(endDate),
    levelsCount: 10,
  })

  // Utility function to format numbers
  const formatNumber = (value: number | string): string => {
    const num = typeof value === 'string' ? parseFloat(value) : value
    if (isNaN(num)) return String(value)

    if (num >= 1000000000) {
      return (num / 1000000000).toFixed(2).replace(/\.00$/, '') + 'B'
    } else if (num >= 1000000) {
      return (num / 1000000).toFixed(2).replace(/\.00$/, '') + 'M'
    } else if (num >= 1000) {
      return (num / 1000).toFixed(2).replace(/\.00$/, '') + 'K'
    }
    return num.toFixed(2).replace(/\.00$/, '')
  }

  // Use useEffect to update chart when data arrives
  useEffect(() => {
    // If data is still loading or empty, reset to loading state
    if (
      levelsLoading ||
      !symbolLevels?.dark ||
      symbolLevels.dark.length === 0
    ) {
      setChartOptions({
        chart: {
          height: height,
          type: 'bar',
          toolbar: { show: false },
        },
        noData: { text: 'Loading...' },
      })
      setSeries([])
      return
    }

    // Prepare data for the chart
    const levels = symbolLevels.dark.map((level: LevelData) => level.price)
    const premiums = symbolLevels.dark.map(
      (level: LevelData) => level.net_premium
    )

    // Create new chart options
    const newChartOptions: ApexOptions = {
      chart: {
        height: height,
        type: 'bar',
        events: {
          mounted: (chart) => {
            chart.windowResizeHandler()
          },
        },
        toolbar: {
          show: false,
        },
      },
      plotOptions: {
        bar: {
          borderRadius: 4,
          horizontal: false,
          columnWidth: '50%',
        },
      },
      colors: ['#06b6d4'],
      grid: {
        borderColor: 'rgba(255, 255, 255, 0.3)',
      },
      dataLabels: {
        enabled: false,
      },
      xaxis: {
        categories: levels,
        labels: {
          show: true,
          style: {
            colors: '#8c9097',
            fontSize: '11px',
            fontWeight: 600,
          },
          formatter: formatNumber,
        },
      },
      yaxis: {
        labels: {
          show: true,
          style: {
            colors: '#8c9097',
            fontSize: '11px',
            fontWeight: 600,
          },
          formatter: formatNumber,
        },
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

    // Update state with new options and series
    setChartOptions(newChartOptions)
    setSeries([{ name: 'Premium', data: premiums }])
  }, [symbolLevels, levelsLoading, height])

  // If still loading, show loader
  if (levelsLoading) {
    return <Loader />
  }

  return (
    <ReactApexChart
      height={height}
      options={chartOptions}
      series={series}
      type='bar'
      width={'100%'}
    />
  )
}

export default LitLevelsChart
