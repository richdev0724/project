import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import TopBullishNetPremiumProgressBar from './top-bullish-net-premium-progress-bar'
import { useEffect, useState } from 'react'
import Loader from '@/components/loader'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTrigger,
  DialogClose,
} from '@/components/ui/dialog'
import { topNetImpactService } from '@/api/top-net-impact.service'
import type { TopNetImpactItem } from '@/api/top-net-impact.service'

const TopBullishNetPremium = () => {
  const [largestValue, setLargestValue] = useState<number>(0)
  const [bullishData, setBullishData] = useState<TopNetImpactItem[]>([])
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [isError, setIsError] = useState<boolean>(false)

  // Fetch data function
  const fetchData = async () => {
    try {
      const response = await topNetImpactService.getTopNetImpact()

      if (response.success && response.data.bullish) {
        setBullishData(response.data.bullish)
        setIsError(false)
      } else {
        setIsError(true)
      }
    } catch (error) {
      setIsError(true)
    } finally {
      setIsLoading(false)
    }
  }

  // Initial fetch and polling every minute
  useEffect(() => {
    fetchData() // Initial fetch

    const interval = setInterval(() => {
      fetchData() // Poll every minute
    }, 60000) // 60000ms = 1 minute

    return () => clearInterval(interval) // Cleanup on unmount
  }, [])

  const formatData = (value: number) => {
    // Handle zero separately
    if (value === 0) return '0'

    // Use absolute value for calculations
    const absValue = Math.abs(value)

    let formattedValue
    if (absValue >= 1000000000) {
      formattedValue = (
        <>
          ${Math.floor(absValue / 1000000000)}
          <span className='text-topBullishNetPremiumPrimaryTransparent'>B</span>
        </>
      )
    } else if (absValue >= 1000000) {
      formattedValue = (
        <>
          ${Math.floor(absValue / 1000000)}
          <span className='text-topBullishNetPremiumPrimaryTransparent'>M</span>
        </>
      )
    } else if (absValue >= 1000) {
      formattedValue = (
        <>
          ${Math.floor(absValue / 1000)}
          <span className='text-topBullishNetPremiumPrimaryTransparent'>K</span>
        </>
      )
    } else {
      formattedValue = absValue.toString()
    }

    return formattedValue
  }

  useEffect(() => {
    if (bullishData && bullishData.length > 0) {
      const largestValue = bullishData.reduce(
        (acc: number, curr: TopNetImpactItem) => {
          if (curr.net_premium > acc) {
            return curr.net_premium
          }
          return acc
        },
        0
      )
      setLargestValue(largestValue)
    }
  }, [bullishData])

  const content = (
    <div className='px-2'>
      <div className='flex items-center justify-between'>
        <h2>Top Bullish Net Premium</h2>
      </div>
      <div className='mt-[20px]'>
        <Table className='text-[13px]'>
          <TableBody>
            {bullishData && bullishData.length > 0 ? (
              bullishData.map((item: TopNetImpactItem, index: number) => (
                <TableRow key={index} className='items-center border-none'>
                  <TableCell className='h-9 w-[40px] pr-2 text-end font-medium'>
                    {item.ticker}
                  </TableCell>
                  <TableCell className='h-8 px-2'>
                    <div className='w-full'>
                      <TopBullishNetPremiumProgressBar
                        currentValue={item.net_premium}
                        largestValue={largestValue}
                        className='bg-gradient-to-r from-topBullishNetPremiumPrimary to-topBullishNetPremiumPrimaryTransparent'
                      />
                    </div>
                  </TableCell>
                  <TableCell className='h-8 w-[25px] pl-2 text-right font-medium'>
                    {formatData(item.net_premium)}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={6} className='h-[200px] text-center'>
                  No records
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )

  // Show loading state
  if (isLoading)
    return (
      <div className='flex h-[444px]'>
        <Loader />
      </div>
    )

  if (isError) {
    return <div></div>
  }

  return (
    <Dialog>
      <DialogTrigger asChild>{content}</DialogTrigger>
      <DialogContent className='w-full max-w-[800px] rounded-[8px] p-6'>
        <DialogHeader className='flex flex-row items-center justify-between text-lg font-medium'>
          <span>Top Bullish Net Premium Details</span>
          <DialogClose className='!mt-0'>Close</DialogClose>
        </DialogHeader>
        {content}
      </DialogContent>
    </Dialog>
  )
}

export default TopBullishNetPremium
