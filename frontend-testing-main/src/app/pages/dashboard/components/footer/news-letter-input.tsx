import { cn } from '@/lib/utils'
import * as React from 'react'

export interface InputSharedProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: React.ReactNode
  inputError?: string
}

const NewsLetterInput = React.forwardRef<HTMLInputElement, InputSharedProps>(
  ({ className, type = 'text', icon, inputError, ...props }, ref) => {
    return (
      <div className='relative flex w-full items-center'>
        <input
          type={type}
          className={cn(
            'flex h-10 w-full rounded-lg border-none bg-transparent px-3 py-1 text-[26px] font-medium shadow-sm transition-colors',
            'file:border-0 file:bg-transparent file:text-sm file:font-medium',
            'placeholder:text-muted-foreground',
            'active:border-none',
            'focus:outline-none', // Add this line to remove focus outline
            // 'disabled:cursor-not-allowed disabled:opacity-50',
            icon && 'pr-12',
            inputError && 'border-red-500',
            className
          )}
          ref={ref}
          {...props}
        ></input>
      </div>
    )
  }
)

export default NewsLetterInput
