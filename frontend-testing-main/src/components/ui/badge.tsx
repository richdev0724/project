import * as React from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from './utils'

const badgeVariants = cva(
  'inline-flex items-center font-gotham font-medium rounded-md border px-2.5 py-0.5 text-xs leading-tight transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
  {
    variants: {
      variant: {
        default:
          'border-transparent bg-primary text-primary-foreground shadow hover:bg-primary/80',
        secondary:
          'border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80',
        destructive:
          'border-transparent bg-destructive text-destructive-foreground shadow hover:bg-destructive/80',
        outline: 'text-foreground',
        bullish: 'border-transparent bg-bgBullish text-textBullish shadow',
        bearish:
          'border-transparent bg-bgBearish text-textBearish shadow border-bearishBorder',
        neutral: 'border-transparent bg-bgNeutral text-textNeutral shadow',
        sizeOi: 'border-transparent bg-bgSizeOI text-textSizeOI shadow',
        aggressive:
          'border-transparent bg-bgAggresive text-textAggresive shadow',
        multiLeg: 'border-transparent bg-bgMultiLeg text-textMultiLeg shadow',
        openingTrade: 'bg-none text-textMultiLeg border-textMultiLeg',
        floor: 'border-transparent bg-bgFloor text-textFloor shadow',
        sweep: 'border-transparent bg-bgSweep text-textSweep shadow',
        cross: 'border-transparent bg-bgCross text-textCross shadow',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  }
)

export type BadgeVariant =
  | 'default'
  | 'secondary'
  | 'destructive'
  | 'outline'
  | 'bullish'
  | 'bearish'
  | 'aggressive'
  | 'neutral'
  | 'multiLeg'
  | 'openingTrade'
  | 'floor'
  | 'sweep'

function Badge({
  className,
  variant,
  asChild = false,
  ...props
}: React.ComponentProps<'span'> &
  VariantProps<typeof badgeVariants> & {
    asChild?: boolean
    variant?: BadgeVariant
  }) {
  const Comp = asChild ? Slot : 'span'

  return (
    <Comp
      data-slot='badge'
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Badge, badgeVariants }
