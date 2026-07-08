import ReactApexChart from 'react-apexcharts'

export default function GexChart({ data }: any) {
  const validContracts = data?.expirations?.[0].contracts.filter(
    (contract: { exposure: number }) => contract?.exposure !== 0
  )
  const strikes = validContracts?.map((contract: string) => contract?.strike)
  const exposures = validContracts?.map(
    (contract: { exposure: any }) => contract?.exposure
  )

  const formatNumber = (num: number) => {
    if (num >= 1000000 || num <= -1000000) {
      const millions = Math.floor(num / 1000000) // Get whole millions
      return millions.toString() + 'M' // Append 'M'
    } else if (num >= 1000 || num <= -1000) {
      const thousands = Math.floor(num / 1000) // Get whole thousands
      return thousands.toString() + 'k' // Append 'k'
    }
    return num?.toString() // Return the number as is if below 1000
  }

  const formatDate = (dateStr: any) => {
    if (typeof dateStr === 'string') {
      const [year, month, day] = dateStr.split('-')
      return `${parseInt(month, 10)}/${parseInt(day, 10)}/${year.slice(2)}`
    }
    // Return the original value or a fallback if dateStr is not a string
    return dateStr || ''
  }
  const formattedStrikes =
    strikes?.map((strike: any) => formatDate(strike)) || []

  const options = {
    series: [
      {
        name: 'Exposure',
        data: exposures || [],
      },
    ],
    options: {
      chart: {
        height: 320,
        toolbar: {
          show: false,
        },
        events: {
          mounted: (chart: any) => {
            chart.windowResizeHandler()
          },
        },
      },
      plotOptions: {
        bar: {
          colors: {
            ranges: [
              {
                from: -Infinity, // This ensures all values less than 0 are included
                to: 0,
                color: '#ef4444', // Red color for negative values
              },
              {
                from: 0,
                to: Infinity,
                color: '#0ea5e9', // Blue color for positive values
              },
            ],
          },
          columnWidth: '80%',
        },
      },
      grid: {
        borderColor: '#f2f5f7',
      },
      colors: ['#0ea5e9', '#ef4444'],
      dataLabels: {
        enabled: false,
      },
      yaxis: {
        title: {
          text: 'Exposure',
          style: {
            color: '#8c9097',
          },
        },
        labels: {
          formatter: function (y: number) {
            const value = parseInt(y.toFixed(0), 10) // Convert the string back to a number
            return formatNumber(value)
          },
        },
      },
      xaxis: {
        categories: formattedStrikes || [],
        labels: {
          rotate: -90,
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
          formatter: (value: any) => formatNumber(value), // Format tooltip values
        },
      },
    },
  }
  return (
    <ReactApexChart
      height={300}
      options={options?.options}
      series={options?.series}
      type='bar'
    />
  )
}
