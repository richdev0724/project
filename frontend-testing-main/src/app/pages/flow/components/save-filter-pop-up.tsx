import { useState } from 'react'
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

export default function SaveFilterPopUp({
  children,
  onSaveFilter,
}: {
  children: React.ReactNode
  onSaveFilter: (value: string) => void
}) {
  const [open, setOpen] = useState(false)

  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      const inputValue = e.currentTarget.value.trim()
      if (inputValue) {
        onSaveFilter(inputValue)
        setOpen(false)
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent
        className='rounded-md bg-[#1A1B20] p-4 shadow-lg'
        onInteractOutside={(e) => e.preventDefault()}
      >
        <fieldset className='flex flex-col gap-2'>
          <label htmlFor='filter-name' className='text-sm text-white'>
            Name your preset, then hit enter
          </label>
          <Input
            id='filter-name'
            type='text'
            aria-label='Preset name'
            className='h-10 rounded-md border-0 bg-[#D9D9D90D] px-4 text-sm font-medium text-white placeholder:text-gray-400 focus:ring-0 active:border-none active:outline-none'
            onKeyDown={handleKeyPress}
          />
        </fieldset>
      </DialogContent>
    </Dialog>
  )
}
