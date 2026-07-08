import ReactApexChart from 'react-apexcharts'

export default function OpeningPositionChart({ data }: any) {
  const formatNumber = (num: string) => {
    // Ensure num is a number by converting it
    const number = parseFloat(num)
    if (isNaN(number)) return num // Return original value if it's not a number

    if (number >= 1000000000) {
      return (number / 1000000000).toFixed(2).replace(/\.00$/, '') + 'B'
    } else if (number >= 1000000) {
      return (number / 1000000).toFixed(2).replace(/\.00$/, '') + 'M'
    } else if (number >= 1000) {
      return (number / 1000).toFixed(2).replace(/\.00$/, '') + 'K'
    }
    return number.toFixed(2).replace(/\.00$/, '')
  }

  const calls =
    data?.map(
      (data: { opening_count_calls: any }) => data?.opening_count_calls
    ) || []
  const callsPerc =
    data?.map(
      (data: { percent_call_opening: any }) => data?.percent_call_opening
    ) || []
  const puts =
    data?.map(
      (data: { opening_count_puts: any }) => data?.opening_count_puts
    ) || []
  const putsPerc =
    data?.map(
      (data: { percent_put_opening: any }) => data?.percent_put_opening
    ) || []

  const dates =
    data?.map((data: any) => {
      const [year, month, day] = data?.date?.split('-') ?? []
      return `${parseInt(month, 10)}-${parseInt(day, 10)}-${year.slice(2)}`
    }) || []

  const options = {
    series: [
      {
        name: 'Calls',
        data: calls,
      },
      {
        name: 'Calls %',
        data: callsPerc,
      },
      {
        name: 'Puts',
        data: puts,
      },
      {
        name: 'Puts %',
        data: putsPerc,
      },
    ],
    options: {
      chart: {
        height: 320,
        events: {
          mounted: (chart: { windowResizeHandler: () => void }) => {
            chart.windowResizeHandler()
          },
        },
        toolbar: {
          show: false, // Disabling the toolbar to remove the hamburger menu
        },
      },
      plotOptions: {
        bar: {
          horizontal: false,
          columnWidth: '80%',
          borderRadius: 4, // Adding rounded corners to the bars
        },
      },
      grid: {
        borderColor: '#f2f5f7',
      },
      dataLabels: {
        enabled: false,
      },
      colors: ['#0ea5e9', '#0d9488', '#ef4444', '#db2777'],
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
          style: {
            color: '#8c9097',
          },
        },
        labels: {
          show: true,
          style: {
            colors: '#8c9097',
            fontSize: '11px',
            fontWeight: 600,
            cssClass: 'apexcharts-xaxis-label',
          },
          formatter: (value: any) => formatNumber(value), // Ensure y-axis labels are formatted
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
    },
  }
  return (
    <ReactApexChart
      height={320}
      options={options?.options}
      series={options?.series}
      type='bar'
    />
  )
}
