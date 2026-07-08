import { useSweeps } from '@/hooks/use-sweeps'
import { ApexOptions } from 'apexcharts'
import { useEffect, useState } from 'react'
import ReactApexChart from 'react-apexcharts'

interface HotContractSweepsProps {
  symbol: string
}

interface ContractData {
  contract: {
    ticker: string
    strike: number
    type: string[]
    expiration: string | number | Date
  }
  data: {
    bought: number
    sold: number
    neutral: number
  }
}

export default function HotContractSweeps({ symbol }: HotContractSweepsProps) {
  const [sweepsData, setSweepsData] = useState<ContractData[]>([])
  const { data: fetchedSweepsData } = useSweeps({ symbol })

  useEffect(() => {
    if (fetchedSweepsData && fetchedSweepsData.length > 0) {
      setSweepsData(fetchedSweepsData)
    }
  }, [fetchedSweepsData])

  const formatDate = (dateStr: string | number | Date) => {
    const date = new Date(dateStr)
    return `${(date.getMonth() + 1).toString().padStart(2, '0')}-${date
      .getDate()
      .toString()
      .padStart(2, '0')}-${date.getFullYear()}`
  }

  const formatNumber = (value: number | string): string => {
    const num = Number(value)
    if (isNaN(num)) return String(value)

    if (num >= 1000000) {
      const millions = num / 1000000
      return millions % 1 === 0 ? `${millions}M` : `${millions.toFixed(1)}M`
    } else if (num >= 1000) {
      const thousands = num / 1000
      return thousands % 1 === 0 ? `${thousands}k` : `${thousands.toFixed(1)}k`
    }
    return num.toString()
  }

  const chartOptions: ApexOptions = {
    series: [
      {
        name: 'Bought $',
        color: '#0ea5e9',
        data: sweepsData.slice(0, 15).map((contractData) => ({
          x: contractData.contract.ticker,
          y: contractData.data.bought,
          fillColor: '#0ea5e9',
        })),
      },
      {
        name: 'Sold $',
        color: '#f43f5e',
        data: sweepsData.slice(0, 15).map((contractData) => ({
          x: contractData.contract.ticker,
          y: contractData.data.sold,
          fillColor: '#f43f5e',
        })),
      },
      {
        name: 'Neutral $',
        color: '#fff',
        data: sweepsData.slice(0, 15).map((contractData) => ({
          x: contractData.contract.ticker,
          y: contractData.data.neutral,
          fillColor: '#fff',
        })),
      },
    ],
    chart: {
      height: 600,
      stacked: true,
      background: 'transparent',
      toolbar: {
        show: false,
      },
    },
    plotOptions: {
      bar: {
        horizontal: true,
        columnWidth: '70%',
      },
    },
    xaxis: {
      categories: sweepsData
        .slice(0, 15)
        .map(
          (contractData) =>
            `${contractData.contract.strike}${contractData.contract.type[0]} ${formatDate(contractData.contract.expiration)}`
        ),
      labels: {
        formatter: formatNumber,
        style: {
          colors: '#fff',
          fontSize: '14px',
        },
      },
    },
    yaxis: {
      labels: {
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
      shared: true,
      intersect: false,
      y: {
        formatter: formatNumber,
      },
    },
    dataLabels: {
      enabled: false,
    },
  }

  return (
    <ReactApexChart
      options={chartOptions}
      series={chartOptions.series}
      type='bar'
      height={600}
    />
  )
}
