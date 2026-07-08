import * as React from 'react'
import * as SliderPrimitive from '@radix-ui/react-slider'

import { cn } from '@/lib/utils'
import AudioThumbIcon from '@/assets/icons/audiothumbIcon'

const CustomSlider = React.forwardRef<
  React.ElementRef<typeof SliderPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof SliderPrimitive.Root>
>(({ className, ...props }, ref) => (
  <SliderPrimitive.Root
    ref={ref}
    className={cn(
      'relative flex w-full touch-none select-none items-center',
      className
    )}
    {...props}
  >
    <SliderPrimitive.Track className='relative h-[2px] w-full grow overflow-hidden rounded-full bg-primary/20'>
      <SliderPrimitive.Range className='absolute h-full bg-[#41E053]' />
    </SliderPrimitive.Track>
    <SliderPrimitive.Thumb className='flex items-center justify-center'>
      <AudioThumbIcon />
    </SliderPrimitive.Thumb>
  </SliderPrimitive.Root>
))
CustomSlider.displayName = SliderPrimitive.Root.displayName

export { CustomSlider }
