import React, { useState, useEffect } from 'react'
import Loader from '@/components/loader'
import { useTopDarkPoolOrders } from '@/hooks/use-top-darkpool-orders'
import { ApexOptions } from 'apexcharts'
import ReactApexChart from 'react-apexcharts'

// Typescript interfaces for type safety
interface DarkPoolLevel {
  price: number
  size: number
}

interface DarkPoolLevelsProps {
  symbol: string
  height?: number
}

const DarkPoolLevels: React.FC<DarkPoolLevelsProps> = ({
  symbol,
  height = 350,
}) => {
  // State to manage chart data and options
  const [chartOptions, setChartOptions] = useState<ApexOptions>({
    chart: {
      type: 'bar',
      height: height,
      toolbar: { show: false },
    },
    noData: { text: 'Loading...' },
  })
  const [series, setSeries] = useState<{ name: string; data: number[] }[]>([])

  // Calculate dates
  const endDate = new Date()
  const startDate = new Date()
  startDate.setDate(endDate.getDate() - 15)

  const formatDate = (date: Date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  const { data: topDarkPoolOrders, isLoading: levelsLoading } =
    useTopDarkPoolOrders({
      symbol: symbol,
      startDate: formatDate(startDate),
      endDate: formatDate(endDate),
      levelsCount: 10,
    })

  const formatNumber = (num: string | number): string => {
    const number = typeof num === 'string' ? parseFloat(num) : num
    if (isNaN(number)) return String(num)

    if (number >= 1000000000) {
      return (number / 1000000000).toFixed(2).replace(/\.00$/, '') + 'B'
    } else if (number >= 1000000) {
      return (number / 1000000).toFixed(2).replace(/\.00$/, '') + 'M'
    } else if (number >= 1000) {
      return (number / 1000).toFixed(2).replace(/\.00$/, '') + 'K'
    }
    return number.toFixed(2).replace(/\.00$/, '').toString()
  }

  // Use useEffect to update chart when data arrives
  useEffect(() => {
    // If data is still loading or empty, reset to loading state
    if (levelsLoading || !topDarkPoolOrders || topDarkPoolOrders.length === 0) {
      setChartOptions({
        chart: {
          type: 'bar',
          height: height,
          toolbar: { show: false },
        },
        noData: { text: 'Loading...' },
      })
      setSeries([])
      return
    }

    // Prepare data for the chart
    const levels = topDarkPoolOrders.map((level: DarkPoolLevel) => level.price)
    const sizes = topDarkPoolOrders.map((level: DarkPoolLevel) => level.size)

    // Create new chart options
    const newChartOptions: ApexOptions = {
      chart: {
        type: 'bar',
        height: height,
        toolbar: { show: false },
      },
      plotOptions: {
        bar: {
          borderRadius: 4,
          horizontal: false,
        },
      },
      colors: ['#7c3aed'],
      grid: {
        borderColor: 'rgba(255, 255, 255, 0.3)',
      },
      dataLabels: { enabled: false },
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
          formatter: (value: number) => formatNumber(value),
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
    setSeries([{ name: 'Volume', data: sizes }])
  }, [topDarkPoolOrders, levelsLoading, height])

  // If still loading, show loader
  if (levelsLoading) {
    return <Loader />
  }

  return (
    <div className='dark-pool-levels'>
      <ReactApexChart
        height={height}
        options={chartOptions}
        series={series}
        type='bar'
      />
    </div>
  )
}

export default DarkPoolLevels
