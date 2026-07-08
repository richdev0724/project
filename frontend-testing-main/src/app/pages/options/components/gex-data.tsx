import { useGammaExposure } from '@/hooks/use-gamma-exposure'
import GexChart from './charts/gex-chart'

interface GexDataProps {
  ticker: string
  expiration: any
}

export default function GexData({ ticker, expiration }: GexDataProps) {
  const { data } = useGammaExposure({
    ticker: ticker,
    expiration: expiration,
  })

  return <GexChart data={data || []} />
}
