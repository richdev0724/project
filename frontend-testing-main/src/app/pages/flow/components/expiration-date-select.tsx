import { cva, type VariantProps } from 'class-variance-authority'
import * as React from 'react'

import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'

import ChevronDownIcon from '@/assets/icons/chevrondown'
import { Badge } from '@/components/ui/badge'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { IconCheck, IconX } from '@tabler/icons-react'
import { Button } from '@/components/custom/button'

/**
 * Variants for the multi-select component to handle different styles.
 * Uses class-variance-authority (cva) to define different styles based on "variant" prop.
 */
const multiSelectVariants = cva(
  'm-1 transition ease-in-out delay-150 hover:-translate-y-1 hover:scale-110 duration-300',
  {
    variants: {
      variant: {
        default:
          'border-foreground/10 text-foreground bg-card hover:bg-card/80',
        secondary:
          'border-foreground/10 bg-secondary text-secondary-foreground hover:bg-secondary/80',
        destructive:
          'border-transparent bg-destructive text-destructive-foreground hover:bg-destructive/80',
        inverted: 'inverted',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  }
)

/**
 * Props for MultiSelect component
 */
interface MultiSelectProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof multiSelectVariants> {
  /**
   * An array of option objects to be displayed in the multi-select component.
   * Each option object has a label, value, and an optional icon.
   */
  options: {
    /** The text to display for the option. */
    label: string
    /** The unique value associated with the option. */
    value: string
    /** Optional icon component to display alongside the option. */
    isOpex?: boolean
  }[]

  /**
   * Callback function triggered when the selected values change.
   * Receives an array of the new selected values.
   */
  onValueChange: (value: string[]) => void

  /** The default selected values when the component mounts. */
  defaultValue?: string[]

  /**
   * Placeholder text to be displayed when no values are selected.
   * Optional, defaults to "Select options".
   */
  placeholder?: string

  /**
   * Animation duration in seconds for the visual effects (e.g., bouncing badges).
   * Optional, defaults to 0 (no animation).
   */
  animation?: number

  /**
   * Maximum number of items to display. Extra selected items will be summarized.
   * Optional, defaults to 3.
   */
  maxCount?: number

  /**
   * The modality of the popover. When set to true, interaction with outside elements
   * will be disabled and only popover content will be visible to screen readers.
   * Optional, defaults to false.
   */
  modalPopover?: boolean

  /**
   * If true, renders the multi-select component as a child of another component.
   * Optional, defaults to false.
   */
  asChild?: boolean

  /**
   * Additional class names to apply custom styles to the multi-select component.
   * Optional, can be used to add custom styles.
   */
  className?: string
  onExposeClearState: (handleClear: () => void) => void
}

export const ExpirationDateMultiSelect = React.forwardRef<
  HTMLButtonElement,
  MultiSelectProps
>(
  (
    {
      onExposeClearState,
      options,
      onValueChange,
      variant,
      defaultValue = [],
      placeholder = '',
      animation = 0,
      maxCount = 3,
      modalPopover = false,
      className,
      ...props
    },
    ref
  ) => {
    const [selectedValues, setSelectedValues] =
      React.useState<string[]>(defaultValue)
    const [isPopoverOpen, setIsPopoverOpen] = React.useState(false)

    const toggleOption = (option: string) => {
      const newSelectedValues = selectedValues.includes(option)
        ? selectedValues.filter((value) => value !== option)
        : [...selectedValues, option]
      setSelectedValues(newSelectedValues)
      onValueChange(newSelectedValues)
    }

    const handleClear = React.useCallback(() => {
      setSelectedValues([])
      onValueChange([])
    }, [onValueChange])

    const handleTogglePopover = () => {
      setIsPopoverOpen((prev) => !prev)
    }

    const clearExtraOptions = () => {
      const newSelectedValues = selectedValues.slice(0, maxCount)
      setSelectedValues(newSelectedValues)
      onValueChange(newSelectedValues)
    }

    React.useEffect(() => {
      setSelectedValues(defaultValue)
    }, [defaultValue])

    React.useEffect(() => {
      onExposeClearState(handleClear)
    }, [])

    return (
      <Popover
        open={isPopoverOpen}
        onOpenChange={setIsPopoverOpen}
        modal={modalPopover}
      >
        <PopoverTrigger className='w-full'>
          <Button
            type='button'
            ref={ref}
            {...props}
            onClick={handleTogglePopover}
            className={cn(
              'flex h-auto min-h-7 w-full items-center justify-between rounded-sm border border-inputBorderColor bg-inputFieldBackground p-1 hover:bg-inputFieldBackground [&_svg]:pointer-events-auto',
              className
            )}
          >
            {selectedValues.length > 0 ? (
              <div className='flex w-full items-center justify-between gap-1'>
                <div className='flex min-w-0 flex-1 flex-wrap items-center gap-0.5'>
                  {selectedValues.slice(0, maxCount).map((value) => {
                    const option = options.find((o) => o.value === value)

                    return (
                      <Badge
                        key={value}
                        className={cn(
                          'm-0.5 h-5 shrink-0 px-1.5 py-0 text-[0.55rem]',
                          multiSelectVariants({ variant })
                        )}
                        style={{ animationDuration: `${animation}s` }}
                      >
                        {option?.label}
                        <IconX
                          className='ml-1 h-3 w-3 cursor-pointer'
                          onClick={(event: { stopPropagation: () => void }) => {
                            event.stopPropagation()
                            toggleOption(value)
                          }}
                        />
                      </Badge>
                    )
                  })}
                  {selectedValues.length > maxCount && (
                    <Badge
                      className={cn(
                        'border-foreground/1 m-0.5 h-5 bg-transparent px-1.5 py-0 text-[0.55rem] text-foreground hover:bg-transparent',
                        multiSelectVariants({ variant })
                      )}
                      style={{ animationDuration: `${animation}s` }}
                    >
                      {`+ ${selectedValues.length - maxCount} more`}
                      <IconX
                        className='ml-1 h-3 w-3 cursor-pointer'
                        onClick={(event: { stopPropagation: () => void }) => {
                          event.stopPropagation()
                          clearExtraOptions()
                        }}
                      />
                    </Badge>
                  )}
                </div>
                <div className='flex shrink-0 items-center justify-center'>
                  <Separator orientation='vertical' className='mx-1 h-4' />
                  <ChevronDownIcon className='h-3 w-3 cursor-pointer text-muted-foreground' />
                </div>
              </div>
            ) : (
              <div className='mx-auto flex w-full items-center justify-between'>
                <span className='mx-2 text-[0.6rem] text-muted-foreground'>
                  {placeholder}
                </span>
                <ChevronDownIcon className='h-3 w-3 cursor-pointer text-muted-foreground' />
              </div>
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent
          className='w-popover-trigger-width p-0'
          align='start'
          onEscapeKeyDown={() => setIsPopoverOpen(false)}
        >
          <Command>
            <CommandList>
              <CommandEmpty>No results found.</CommandEmpty>
              <CommandGroup>
                {options.map((option) => {
                  const isSelected = selectedValues.includes(option.value)
                  return (
                    <CommandItem
                      key={option.value}
                      onSelect={() => toggleOption(option.value)}
                      className='cursor-pointer text-headerPrimary'
                    >
                      <div
                        className={cn(
                          'mr-2 flex h-5 w-5 items-center justify-center rounded-full border border-[#6B7280]',
                          isSelected
                            ? 'bg-[#28BF94] text-primary-foreground'
                            : 'opacity-50 [&_svg]:invisible'
                        )}
                      >
                        <IconCheck className='h-5 w-5' stroke={2} />
                      </div>
                      {option.isOpex ? (
                        <span className='text-[0.65rem] text-yellowHighLightText'>{`${option.label} - OpEx`}</span>
                      ) : (
                        <span className='text-[0.65rem]'>{`${option.label} - Friday`}</span>
                      )}
                    </CommandItem>
                  )
                })}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    )
  }
)

ExpirationDateMultiSelect.displayName = 'ExpirationDateMultiSelect'
