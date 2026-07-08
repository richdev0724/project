import PauseIcon from '@/assets/icons/pauseIcon'
import PlayIcon from '@/assets/icons/playIcon'
import SettingsIcon from '@/assets/icons/settingsIcon'
import SoundOffIcon from '@/assets/icons/soundOffIcon'
import SoundOnIcon from '@/assets/icons/soundOnIcon'
import { Button } from '@/components/custom/button'
import { Dispatch, SetStateAction, useCallback, useEffect } from 'react'
import { DataTablePagination } from './flow-table-pagination'
import SettingsPopover from './settings-popover'

interface FlowTableHeaderTwoProps {
  handlePageChange: (page: number) => void
  pageIndex: number
  pageSize: number
  setPageSize: Dispatch<SetStateAction<number>>
  setSortBy: Dispatch<SetStateAction<'sip_timestamp' | 'net_premium' | 'size'>>
  isOptionsTradeLoadingValue: boolean
  setPageIndex: Dispatch<SetStateAction<number>>
  totalCount: number
  stopListeningToOptionsTrade: () => void
  startListeningToOptionsTrade: () => void
  showSoundIcon: boolean
  isSoundOff: boolean
  children?: React.ReactNode
}

export default function FlowTableHeaderTwo({
  handlePageChange,
  pageIndex,
  pageSize,
  setPageSize,
  setSortBy,
  setPageIndex,
  isOptionsTradeLoadingValue,
  totalCount,
  stopListeningToOptionsTrade,
  startListeningToOptionsTrade,
  showSoundIcon,
  isSoundOff,
  children,
}: FlowTableHeaderTwoProps) {
  const handleOptionsTradeToggle = useCallback(() => {
    if (isOptionsTradeLoadingValue) {
      stopListeningToOptionsTrade()
    } else {
      startListeningToOptionsTrade()
    }
  }, [
    isOptionsTradeLoadingValue,
    startListeningToOptionsTrade,
    stopListeningToOptionsTrade,
  ])

  useEffect(() => {
    setPageIndex(0)
  }, [pageSize])

  return (
    <div className='flex w-full items-center justify-between'>
      <div className='flex items-center'>{children}</div>
      <div className='flex items-center justify-center gap-7'>
        <div>
          <DataTablePagination
            pageIndex={pageIndex}
            pageCount={Math.ceil(totalCount / pageSize)}
            handlePageChange={handlePageChange}
          />
        </div>

        <Button
          size='icon'
          variant='ghost'
          className='flex h-6 w-6 items-center justify-center'
          onClick={handleOptionsTradeToggle}
        >
          {!isOptionsTradeLoadingValue ? <PlayIcon /> : <PauseIcon />}
        </Button>

        <Button
          size='icon'
          variant='ghost'
          className='flex h-[22px] w-[22px] items-center justify-center'
        >
          {!showSoundIcon && isSoundOff ? <SoundOffIcon /> : <SoundOnIcon />}
        </Button>

        <SettingsPopover
          setPageSize={setPageSize}
          pageSize={pageSize}
          setSortBy={setSortBy}
        >
          <Button
            size='default'
            variant='secondary'
            className='flex h-12 items-center justify-center px-4'
          >
            <SettingsIcon fill='#fff' height={13} width={13} />
            <span className='ml-2 hidden text-xs font-normal md:inline'>
              Settings
            </span>
          </Button>
        </SettingsPopover>
      </div>
    </div>
  )
}
