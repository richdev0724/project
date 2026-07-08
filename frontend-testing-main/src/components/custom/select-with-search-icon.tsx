import ChevronDownCustomIcon from '@/assets/icons/chevrondown'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { IconSearch } from '@tabler/icons-react'
import { FC, useState } from 'react'

interface MakeSearchDropdownProps {
  placeholder?: string
  hasError: boolean
}

export const MakeSearchDropdown: FC<MakeSearchDropdownProps> = ({
  placeholder,
  hasError,
}) => {
  const [open, setOpen] = useState(false)

  const handlePopoverOpenChange = (isOpen: boolean) => {
    setOpen(isOpen)
  }

  return (
    <Popover open={open} onOpenChange={handlePopoverOpenChange} modal={true}>
      <PopoverTrigger asChild>
        <button
          aria-expanded={open}
          className={cn(
            'relative flex h-9 w-full cursor-pointer items-center justify-between border border-inputBorderColor lg:w-[240px]',
            'rounded-sm px-2 py-1 text-sm font-medium shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
            open && '',
            hasError && 'ring-red focus-visible:ring-red border border-input'
          )}
        >
          <span className={cn('truncate', 'flex gap-2 text-muted-foreground')}>
            <IconSearch size={18} stroke={2} />
            {placeholder || 'Select option'}
          </span>

          <ChevronDownCustomIcon className='ml-1' height={12} />
        </button>
      </PopoverTrigger>
      <PopoverContent className='w-popover-trigger-width p-0'>
        {open && (
          <Command shouldFilter={false}>
            <CommandInput
              placeholder={`${placeholder}...`}
              className='w-full'
            />
            <CommandList className='max-h-full'>
              <CommandEmpty>No results found.</CommandEmpty>
              <CommandGroup>
                <div className='max-h-[300px] min-h-12 overflow-y-auto'>
                  {/* Static Options */}
                  <CommandItem
                    value='1'
                    className='cursor-pointer hover:bg-accent'
                  >
                    Example Make 1
                  </CommandItem>
                  <CommandItem
                    value='2'
                    className='cursor-pointer hover:bg-accent'
                  >
                    Example Make 2
                  </CommandItem>
                  <CommandItem
                    value='3'
                    className='cursor-pointer hover:bg-accent'
                  >
                    Example Make 3
                  </CommandItem>
                </div>
              </CommandGroup>
            </CommandList>
          </Command>
        )}
      </PopoverContent>
    </Popover>
  )
}
