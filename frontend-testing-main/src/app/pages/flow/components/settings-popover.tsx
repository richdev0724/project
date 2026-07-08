import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { PopoverClose } from '@radix-ui/react-popover'
import { IconX } from '@tabler/icons-react'
import { Dispatch, SetStateAction } from 'react'

/**
 * A component that renders a settings popover UI element.
 *
 * The popover allows users to configure settings related to pagination and sorting.
 * It includes options to select the number of rows per page and the sorting criteria.
 *
 * @param {React.ReactNode} children - The child component that triggers the popover.
 * @param {number} pageSize - The current number of rows displayed per page.
 * @param {Dispatch<SetStateAction<number>>} setPageSize - Function to update the page size.
 * @param {Dispatch<SetStateAction<'sip_timestamp' | 'net_premium' | 'size'>>} setSortBy - Function to update the sorting criteria.
 */

export default function SettingsPopover({
  children,
  pageSize,
  setPageSize,
  setSortBy,
}: {
  children: React.ReactNode
  pageSize: number
  setPageSize: Dispatch<SetStateAction<number>>
  setSortBy: Dispatch<SetStateAction<'sip_timestamp' | 'net_premium' | 'size'>>
}) {
  const handlePageSizeChange = (index: number) => {
    setPageSize(index)
  }

  const handleSortByChange = (
    value: 'sip_timestamp' | 'net_premium' | 'size'
  ) => {
    setSortBy(value)
  }

  return (
    <Popover>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent
        align='end'
        sideOffset={10}
        className='relative right-2 h-[139px] w-[287px] space-y-3 rounded-sm border-popOverBorder  bg-background p-0'
      >
        <PopoverClose className='absolute right-0 top-0 p-1'>
          <IconX stroke={2} size={13} />
        </PopoverClose>
        <div className='px-3 pb-4 pt-8'>
          <div className='space-y-4'>
            <div className='flex items-center justify-end'>
              <span className='text-sm text-primary'>Rows per page</span>
              <div className='ml-8'>
                <Select
                  defaultValue={pageSize.toString()}
                  onValueChange={(value: any) => {
                    handlePageSizeChange(Number(value))
                  }}
                >
                  <SelectTrigger className='h-7 w-28 rounded-sm border-primary px-1  text-primary'>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className='bg-background'>
                    <SelectItem value='50'>50</SelectItem>
                    <SelectItem value='100'>100</SelectItem>
                    <SelectItem value='150'>150</SelectItem>
                    <SelectItem value='200'>200</SelectItem>
                    <SelectItem value='250'>250</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className='flex items-center justify-end'>
              <span className='text-sm text-primary '>Sort by</span>
              <div className='ml-8'>
                <Select
                  defaultValue='sip_timestamp'
                  onValueChange={handleSortByChange}
                >
                  <SelectTrigger className='h-7 w-28 rounded-sm border-primary px-1 text-primary '>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className='bg-background'>
                    <SelectItem value='sip_timestamp'>Time</SelectItem>
                    <SelectItem value='net_premium'>Premium</SelectItem>
                    <SelectItem value='size'>Size</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}
