import PlayIconTickerOverview from '@/assets/icons/playIconTickerOverview'
import { isShowAudioPlayerAtom } from '@/store/store'
import { useAtom } from 'jotai'
import { useParams } from 'react-router-dom'
import { useEarning } from '@/hooks/use-earning'
import { useMemo } from 'react'

// Helper function to parse YYYY-MM-DD date strings without timezone issues
const parseDate = (dateString: string | null | undefined): Date | null => {
  if (!dateString) return null
  const [year, month, day] = dateString.split('-').map(Number)
  return new Date(year, month - 1, day)
}

const TickerEarningsAudioTab = () => {
  const [, setIsPlaying] = useAtom(isShowAudioPlayerAtom)
  const { symbol } = useParams()
  const { data: earningsData, isLoading } = useEarning(symbol || '')

  const handleClickPlayAudio = () => {
    setIsPlaying((prev) => !prev)
  }

  // Get the next earnings report (future date)
  const nextEarnings = useMemo(() => {
    if (!earningsData || earningsData.length === 0) return null

    const now = new Date()
    now.setHours(0, 0, 0, 0) // Reset to start of day for comparison

    // Look for future earnings - these won't have actual_eps yet
    const futureEarnings = earningsData
      .filter((item) => {
        if (!item.report_date) return false
        const date = parseDate(item.report_date)
        return date && date >= now
      })
      .sort((a, b) => {
        const dateA = parseDate(a.report_date)
        const dateB = parseDate(b.report_date)
        if (!dateA || !dateB) return 0
        return dateA.getTime() - dateB.getTime()
      })

    // Return the next upcoming earnings
    if (futureEarnings.length > 0) {
      return futureEarnings[0]
    }

    // If no future earnings, return the most recent one
    const sortedData = [...earningsData]
      .filter((item) => item.report_date)
      .sort((a, b) => {
        const dateA = parseDate(a.report_date)
        const dateB = parseDate(b.report_date)
        if (!dateA || !dateB) return 0
        return dateB.getTime() - dateA.getTime() // Descending - most recent first
      })

    return sortedData.length > 0 ? sortedData[0] : null
  }, [earningsData])

  // Format date and time
  const formattedDateTime = useMemo(() => {
    if (!nextEarnings || !nextEarnings.report_date) return 'TBA'

    const date = parseDate(nextEarnings.report_date)
    if (!date) return 'TBA'

    const options: Intl.DateTimeFormatOptions = {
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    }
    const dateStr = date.toLocaleDateString('en-US', options)

    let timeStr = ''
    if (nextEarnings.report_time === 'premarket') {
      timeStr = '07:00 AM'
    } else if (nextEarnings.report_time === 'postmarket') {
      timeStr = '04:00 PM'
    } else {
      timeStr = 'TBA'
    }

    return `${dateStr}, ${timeStr}`
  }, [nextEarnings])

  // Extract quarter for display
  const quarterLabel = useMemo(() => {
    if (!nextEarnings || !nextEarnings.ending_fiscal_quarter) return 'TBA'

    const date = parseDate(nextEarnings.ending_fiscal_quarter)
    if (!date) return 'TBA'

    const month = date.getMonth() + 1
    let quarter = 'Q1'
    if (month >= 1 && month <= 3) quarter = 'Q1'
    else if (month >= 4 && month <= 6) quarter = 'Q2'
    else if (month >= 7 && month <= 9) quarter = 'Q3'
    else quarter = 'Q4'

    const year = date.getFullYear()
    return `${quarter} ${year}`
  }, [nextEarnings])

  if (isLoading || !nextEarnings) {
    return (
      <div className='absolute bottom-0 left-0 right-0 flex h-[92px] justify-around rounded-sm rounded-tl-none bg-[#2D2D2D] p-5 shadow-[0_-8px_12px_-3px_rgba(0,0,0,0.3)]'>
        <div className='flex flex-col justify-start text-xs'>
          <p className='pb-1 font-medium'>Next Earnings Report</p>
          <span className='folder-div absolute -top-[10px] left-0 h-[10px] w-[150px] bg-[#2D2D2D]'></span>
          <p className='-mt-1.5 font-light text-gray-400'>Loading...</p>
        </div>
      </div>
    )
  }

  return (
    <div className='absolute bottom-0 left-0 right-0 flex h-[92px] justify-around rounded-sm rounded-tl-none bg-[#2D2D2D] p-5 shadow-[0_-8px_12px_-3px_rgba(0,0,0,0.3)]'>
      <div className='flex flex-col justify-start text-xs'>
        <p className='pb-1 font-medium'>Next Earnings Report</p>
        <span className='folder-div absolute -top-[10px] left-0 h-[10px] w-[150px] bg-[#2D2D2D]'></span>
        <p className='-mt-1.5 font-light'>{formattedDateTime}</p>
        <div>
          <span className='font-medium'>EPS</span>{' '}
          <span className='font-light'>
            <span className='text-[#28BF94]'>
              ${nextEarnings.street_mean_est}
            </span>{' '}
            (est)
          </span>
        </div>
      </div>
      <div className='-ml-2 flex h-[54px] w-[174px] items-center justify-between rounded-sm border border-[#28BF94] bg-[#1A1B20] p-4'>
        <div onClick={handleClickPlayAudio} className='cursor-pointer'>
          <PlayIconTickerOverview />
        </div>
        <div className='flex flex-col items-center text-sm'>
          <span className='font-medium'>{quarterLabel} ER</span>{' '}
          <span className='-mt-2 font-light'>Listen Now</span>
        </div>
      </div>
    </div>
  )
}

export default TickerEarningsAudioTab
