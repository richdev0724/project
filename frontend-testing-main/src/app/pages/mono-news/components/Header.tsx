import { Search, Settings } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/custom/button'

interface HeaderProps {
  searchQuery: string
  onSearchChange: (value: string) => void
  onSettingsClick: () => void
}

export function Header({
  searchQuery,
  onSearchChange,
  onSettingsClick,
}: HeaderProps) {
  return (
    <header className='sticky top-0 z-20 bg-card'>
      <div className='flex items-center justify-between gap-4 px-5 py-1.5'>
        <div className='flex items-center gap-6'>
          <nav className='hidden gap-4 md:flex'>
            <a
              href='#'
              className='font-gothamLight text-xs text-muted-foreground transition-colors hover:text-foreground'
            >
              Markets
            </a>
            <a
              href='#'
              className='font-gothamLight text-xs text-muted-foreground transition-colors hover:text-foreground'
            >
              Economics
            </a>
            <a
              href='#'
              className='font-gothamLight text-xs text-muted-foreground transition-colors hover:text-foreground'
            >
              Industries
            </a>
            <a
              href='#'
              className='font-gothamLight text-xs text-muted-foreground transition-colors hover:text-foreground'
            >
              Tech
            </a>
          </nav>
        </div>

        <div className='flex items-center gap-2'>
          <div className='relative max-w-md flex-1'>
            <Search className='absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground' />
            <Input
              type='text'
              placeholder='Search by ticker'
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              className='h-8 rounded-sm border-inputBorderColor bg-inputFieldBackground pl-8 text-xs font-medium placeholder:text-placeHolderColor focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50'
            />
          </div>

          <Button
            variant='outline'
            size='icon'
            onClick={onSettingsClick}
            className='h-8 w-8'
          >
            <Settings className='h-3.5 w-3.5 text-foreground' />
          </Button>
        </div>
      </div>
    </header>
  )
}
