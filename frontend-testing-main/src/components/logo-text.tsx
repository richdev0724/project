import { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

interface LogoTextProps extends HTMLAttributes<HTMLDivElement> {
  size?: 'sm' | 'md' | 'lg' | 'xl'
}

const sizeClasses = {
  sm: 'text-4xl',
  md: 'text-5xl',
  lg: 'text-6xl',
  xl: 'text-7xl',
}

export const LogoText = ({
  size = 'md',
  className,
  ...props
}: LogoTextProps) => {
  return (
    <div
      className={cn(
        'select-none font-k2d font-bold leading-none text-[#F5F7F7]',
        sizeClasses[size],
        className
      )}
      {...props}
    >
      M
    </div>
  )
}

export default LogoText
