import { Button } from '@/components/custom/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { IconChevronDown, IconChevronUp } from '@tabler/icons-react'
import { useState } from 'react'

/**
 * SectorOverview component
 *
 * @param {object} props - Component props
 * @prop {string} selectedItem - Selected time period
 * @prop {function} setSelectedItem - Callback to set the selected time period
 *
 * @returns {React.ReactElement}
 */

interface SectorOverviewProps {
  selectedItem: 'Today' | 'Weekly' | 'Monthly'
  setSelectedItem: (item: 'Today' | 'Weekly' | 'Monthly') => void
}

export function SectorOverview({
  selectedItem,
  setSelectedItem,
}: SectorOverviewProps) {
  const [isOpen, setIsOpen] = useState<boolean>(false)

  const handleItemSelect = (item: 'Today' | 'Weekly' | 'Monthly') => {
    setSelectedItem(item)
    setIsOpen(false)
  }

  return (
    <DropdownMenu open={isOpen} onOpenChange={setIsOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          onClick={() => setIsOpen(!isOpen)}
          className='mt-0 flex items-center rounded-full border border-muted bg-greyBackground px-2  text-muted-foreground hover:bg-greyBackground/80'
        >
          {selectedItem}
          {isOpen ? (
            <IconChevronUp className='ml-2 h-4 w-4' />
          ) : (
            <IconChevronDown className='ml-2 h-4 w-4' />
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuItem onClick={() => handleItemSelect('Today')}>
          Today
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => handleItemSelect('Weekly')}>
          Weekly
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => handleItemSelect('Monthly')}>
          Monthly
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
