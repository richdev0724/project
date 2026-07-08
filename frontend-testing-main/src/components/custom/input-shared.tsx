import * as React from 'react'
import { cn } from '@/lib/utils'

export interface InputSharedProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: React.ReactNode
  inputError?: string
}

const InputShared = React.forwardRef<HTMLInputElement, InputSharedProps>(
  ({ className, type = 'text', icon, inputError, ...props }, ref) => {
    return (
      <>
        <div className='relative flex w-full items-center'>
          <input
            type={type}
            className={cn(
              'flex h-10 w-full rounded-lg border border-muted bg-transparent px-3 py-2 text-sm font-normal shadow-sm transition-colors',
              'file:border-0 file:bg-transparent file:text-sm file:font-medium',
              'placeholder:text-sm placeholder:text-muted-foreground',
              'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
              'disabled:cursor-not-allowed disabled:opacity-50',
              icon && 'pr-12',
              inputError && 'border-red-500',
              className
            )}
            ref={ref}
            {...props}
          />
        </div>
      </>
    )
  }
)

InputShared.displayName = 'InputShared'

export { InputShared }
