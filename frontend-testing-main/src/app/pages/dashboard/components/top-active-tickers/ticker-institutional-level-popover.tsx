import LitLevelsChart from '@/app/pages/options/components/charts/lit-levels.chart'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { ReactElement } from 'react'

export default function TickerInstitutionalLevelPopover({
  children,
  ticker,
}: {
  children: ReactElement
  ticker: string
}) {
  return (
    <Popover>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent className='h-96 w-80 overflow-auto'>
        <div className='grid gap-4'>
          <h3 className='text-sm font-semibold'>{ticker}</h3>
          <div className='space-y-2'>
            <div className='items-center space-y-2'>
              <h4 className='text-xs font-semibold'>Institutional Levels</h4>
              <LitLevelsChart height={200} symbol={'TSLA'} />
            </div>
          </div>
          <div className='space-y-2'>
            <div className='items-center space-y-2'>
              <h4 className='text-xs font-semibold'>DarkPool Levels</h4>
              <LitLevelsChart height={200} symbol={'TSLA'} />
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}
