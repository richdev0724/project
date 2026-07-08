import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { IconPointFilled } from '@tabler/icons-react'
import * as React from 'react'

export interface PasswordInputProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  showTooltip?: boolean
  inputError?: string
  isPassword?: boolean
  type?: string
}

const PasswordInput = React.forwardRef<HTMLInputElement, PasswordInputProps>(
  (
    {
      className,
      value = '',
      showTooltip = true,
      isPassword = false,
      type = 'password',
      inputError,
      ...props
    },
    ref
  ) => {
    const [tooltipOpen, setTooltipOpen] = React.useState(false)

    const requirements = [
      {
        label: 'Use at least 8 characters',
        test: (pass: string) => pass.length >= 8,
      },
      {
        label: 'Use upper and lower case characters',
        test: (pass: string) => /[a-z]/.test(pass) && /[A-Z]/.test(pass),
      },
      {
        label: 'Use at least one number',
        test: (pass: string) => /[0-9]/.test(pass),
      },
      {
        label: 'Use at least one special character',
        test: (pass: string) => /[!@#$%^&*(),.?":{}|<>]/.test(pass),
      },
    ]

    const handleFocus = (event: React.FocusEvent<HTMLInputElement>) => {
      if (showTooltip) {
        setTooltipOpen(true)
      }

      props.onFocus?.(event)
    }

    const handleBlur = (event: React.FocusEvent<HTMLInputElement>) => {
      if (showTooltip) {
        setTooltipOpen(false)
      }

      props.onBlur?.(event)
    }

    // Calculate progress
    const progress =
      (requirements.filter((req) => req.test(value as string)).length /
        requirements.length) *
      100

    // Determine password strength status
    const getPasswordStrengthStatus = () => {
      if (progress < 50)
        return {
          status: 'Weak',
          color: '#F12617',
          message: 'Your password powers are weak old man...',
        }
      if (progress < 75)
        return {
          status: 'Not Great',
          color: '#46552C',
          message: 'You must unlearn what you have learned...',
        }
      return {
        status: 'Strong',
        color: '#4CAF50',
        message: 'This password brings balance to the Force.',
      }
    }

    const { status, color, message } = getPasswordStrengthStatus()

    return (
      <TooltipProvider>
        <div className='relative'>
          <Tooltip open={tooltipOpen && isPassword}>
            <TooltipTrigger asChild>
              <input
                type={type}
                className={cn(
                  'flex h-10 w-full rounded-lg border border-muted bg-transparent px-3 py-2 text-sm font-normal shadow-sm transition-colors',
                  'file:border-0 file:bg-transparent file:text-sm file:font-medium',
                  'placeholder:text-sm placeholder:text-muted-foreground',
                  'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
                  'disabled:cursor-not-allowed disabled:opacity-50',
                  'pr-12',
                  inputError && 'border-red-500',
                  className
                )}
                ref={ref}
                {...props}
                value={value}
                onFocus={handleFocus}
                onBlur={handleBlur}
              />
            </TooltipTrigger>
            <TooltipContent
              side='left'
              className='relative w-96 space-y-4 rounded-lg  bg-transparent'
              sideOffset={20}
            >
              <div className='relative rounded-md border-2 border-[#313131] bg-[#272930] p-4 text-white shadow-xl'>
                <div className='absolute right-[-10px] top-1/2 z-50 h-6 w-6 -translate-y-1/2 rotate-45 border-none bg-[#272930]'></div>
                <div className='space-y-4'>
                  <h4 className='text-sm font-medium'>
                    We take passwords very seriously...
                    <br />
                    like, really, really seriously.
                  </h4>
                  <ul className='space-y-2'>
                    {requirements.map((req, index) => {
                      const isMet = req.test(value as string)
                      return (
                        <li key={index} className='flex items-center space-x-2'>
                          <IconPointFilled className='h-4 w-4 ' />
                          <span
                            className={`text-sm ${
                              isMet ? 'line-through' : 'text-[#F5F7F7]'
                            }`}
                          >
                            {req.label}
                          </span>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              </div>
            </TooltipContent>
          </Tooltip>

          {/* Progress Bar */}
          {isPassword && (
            <div className='mt-2'>
              <div className='h-2 w-full rounded-[2px] bg-[#8C8C8C]'>
                <div
                  className='h-2 rounded-full'
                  style={{
                    width: `${progress}%`,
                    backgroundColor: color,
                  }}
                ></div>
              </div>

              {isPassword && value !== '' && (
                <>
                  <div className='mt-1 text-sm font-bold text-[#FFFFFF]'>
                    {status}
                  </div>

                  {/* Status Message */}
                  <div className='text-sm font-light text-[#FFFFFF]'>
                    {message}
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      </TooltipProvider>
    )
  }
)

PasswordInput.displayName = 'PasswordInput'

export { PasswordInput }
