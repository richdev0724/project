import ReactApexChart from 'react-apexcharts'
import { useEffect, useState } from 'react'
import { ApexOptions } from 'apexcharts'

export default function FlowContractChart() {
  const [chartWidth, setChartWidth] = useState(0)

  useEffect(() => {
    const updateDimensions = () => {
      const chartContainer = document.getElementById('chart-container')
      if (chartContainer) {
        setChartWidth(chartContainer.offsetWidth)
      }
    }

    updateDimensions()

    window.addEventListener('resize', updateDimensions)
    return () => window.removeEventListener('resize', updateDimensions)
  }, [])

  const options: ApexOptions = {
    chart: {
      zoom: {
        enabled: false,
      },
      toolbar: {
        show: false,
      },
      animations: {
        enabled: true,
        animateGradually: {
          enabled: true,
          delay: 150,
        },
      },
    },
    legend: {
      show: false,
    },
    stroke: {
      width: [0, 2, 1],
      curve: 'straight',
      dashArray: [0, 0, 5],
      colors: ['#fff', '#fff'],
    },
    plotOptions: {
      bar: {
        columnWidth: '10%',
      },
    },

    grid: {
      position: 'back',
      row: {
        colors: ['transparent'], // Alternating row colors
        opacity: 0.5,
      },
      xaxis: {
        lines: {
          show: false,
        },
      },
      yaxis: {
        lines: {
          show: true,
        },
      },
    },
    labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep'],
    yaxis: [
      {
        title: {
          text: '',
        },
      },
      {
        opposite: true,
        title: {
          text: '',
        },
      },
    ],
    responsive: [
      {
        breakpoint: 640,
        options: {
          chart: {
            height: 300,
          },
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
      name: 'Website Blog',
      type: 'column',
      data: [440, 505, 414, 671, 227, 413, 201, 352, 752],
      //   color: '#fff',
    },
    {
      name: 'Social Media',
      type: 'line',
      data: [23, 42, 35, 27, 43, 22, 17, 31, 22],
      //   color: '#fff',
    },
    {
      name: 'Marketing',
      type: 'line',
      data: [30, 45, 32, 38, 40, 50, 38, 41, 36],
      //   color: '#fff',
    },
  ]

  return (
    <div id='chart-container' className='w-full'>
      <ReactApexChart
        options={options}
        series={series}
        type='line'
        height={chartWidth < 640 ? 240 : 320}
        width='100%'
      />
    </div>
  )
}
