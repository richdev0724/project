import { BearActivityChart } from '@/components/custom/md-components/barcharts/bearActivity'
import Loader from '@/components/loader'
import { useContractsTopActivity } from '@/hooks/use-contracts-top-activity'

export default function BearActivityData() {
  const currentDate = new Date()
  const endDate = currentDate.toISOString().split('T')[0]

  currentDate.setMonth(currentDate.getMonth() - 2)
  const startDate = currentDate.toISOString().split('T')[0]

  const { contractsTopActivity, isLoading, isError } = useContractsTopActivity({
    startDate,
    endDate,
  })

  if (isLoading) {
    return <Loader />
  }

  if (isError) {
    return <div>Error</div>
  }

  return <BearActivityChart tickers={contractsTopActivity?.sold ?? []} />
}
