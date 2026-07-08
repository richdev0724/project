'use client'

import * as React from 'react'
import * as TooltipPrimitive from '@radix-ui/react-tooltip'

import { cn } from './utils'

function TooltipProvider({
  delayDuration = 0,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Provider>) {
  return (
    <TooltipPrimitive.Provider
      data-slot='tooltip-provider'
      delayDuration={delayDuration}
      {...props}
    />
  )
}

const TooltipContext = React.createContext<{
  open: boolean
  setOpen: (open: boolean) => void
}>({ open: false, setOpen: () => {} })

function Tooltip({
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Root>) {
  const [open, setOpen] = React.useState(false)

  // Close tooltip on any click
  React.useEffect(() => {
    if (open) {
      const handleClick = () => {
        setOpen(false)
      }

      // Use capture phase to ensure we catch all clicks
      document.addEventListener('click', handleClick, true)

      return () => {
        document.removeEventListener('click', handleClick, true)
      }
    }
  }, [open])

  return (
    <TooltipContext.Provider value={{ open, setOpen }}>
      <TooltipPrimitive.Root
        data-slot='tooltip'
        open={open}
        disableHoverableContent={true}
        {...props}
      />
    </TooltipContext.Provider>
  )
}

function TooltipTrigger({
  onClick,
  onPointerEnter,
  onPointerLeave,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Trigger>) {
  const { open, setOpen } = React.useContext(TooltipContext)

  return (
    <TooltipPrimitive.Trigger
      data-slot='tooltip-trigger'
      onClick={(e) => {
        // Stop propagation to prevent the document click listener from immediately closing
        e.stopPropagation()
        setOpen(!open)
        onClick?.(e)
      }}
      onPointerEnter={(e) => {
        // Prevent default hover behavior
        e.preventDefault()
      }}
      onPointerLeave={(e) => {
        // Prevent default hover behavior
        e.preventDefault()
      }}
      {...props}
    />
  )
}

function TooltipContent({
  className,
  sideOffset = 0,
  children,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Content>) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        data-slot='tooltip-content'
        sideOffset={sideOffset}
        className={cn(
          'origin-(--radix-tooltip-content-transform-origin) z-50 w-fit text-balance rounded-md bg-primary px-3 py-1.5 text-xs text-primary-foreground animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2',
          className
        )}
        {...props}
      >
        {children}
        <TooltipPrimitive.Arrow className='z-50 size-2.5 translate-y-[calc(-50%_-_2px)] rotate-45 rounded-[2px] bg-primary fill-primary' />
      </TooltipPrimitive.Content>
    </TooltipPrimitive.Portal>
  )
}

export { Tooltip, TooltipTrigger, TooltipContent, TooltipProvider }
