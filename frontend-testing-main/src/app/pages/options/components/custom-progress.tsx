import { Progress } from '@radix-ui/react-progress'
import { IconCircleFilled } from '@tabler/icons-react'
import { useMemo } from 'react'

interface ProgressBarProps {
  start: number
  end: number
  breakpoint1: number
  breakpoint2: number
}

export function ProgressBar({
  start,
  end,
  breakpoint1,
  breakpoint2,
}: ProgressBarProps) {
  const range = end - start

  // Calculate positions with 5% padding on each side
  const positions = useMemo(() => {
    const normalizedBreakpoint1 = ((breakpoint1 - start) / range) * 90 + 5
    const normalizedBreakpoint2 = ((breakpoint2 - start) / range) * 90 + 5

    return {
      left: `${normalizedBreakpoint1}%`,
      width: `${Math.abs(normalizedBreakpoint2 - normalizedBreakpoint1)}%`,
      fillStart: `${Math.min(normalizedBreakpoint1, normalizedBreakpoint2)}%`,
      isIncreasing: normalizedBreakpoint1 < normalizedBreakpoint2,
    }
  }, [start, end, breakpoint1, breakpoint2, range])

  return (
    <div className='relative mb-7 h-2 w-[200px] rounded-md'>
      <Progress
        value={100}
        max={100}
        className='h-full w-full rounded-md bg-[#0F0F0F]'
      >
        {/* Fill green or red between the breakpoints */}
        {breakpoint1 !== breakpoint2 && (
          <div
            className='absolute left-0 top-0 h-full rounded-md transition-all duration-300'
            style={{
              backgroundColor: positions.isIncreasing ? '#28BF94' : '#FF6B6B',
              width: positions.width,
              left: positions.fillStart,
            }}
          />
        )}
      </Progress>

      {/* Left marker */}
      <div
        className='absolute -bottom-7 transition-all duration-300'
        style={{
          left: positions.left,
          transform: 'translateX(-50%)',
        }}
      >
        <div className='absolute bottom-5 left-[6px] flex w-5 rotate-90 items-center'>
          <IconCircleFilled size={5} />
          <hr className='daily-range-line w-4 border-t-[0.5px] border-[#FFFFFF]' />
          <IconCircleFilled size={5} />
        </div>
        <span className='mt-2 block text-[10px]'>{breakpoint1.toFixed(2)}</span>
      </div>

      {/* Right marker */}
      <div
        className='absolute -bottom-7 transition-all duration-300'
        style={{
          left: positions.fillStart,
          marginLeft: positions.width,
          transform: 'translateX(-50%)',
        }}
      >
        <div className='absolute bottom-5 left-[6px] flex w-5 rotate-90 items-center'>
          <IconCircleFilled size={5} />
          <hr className='daily-range-line w-4 border-t-[0.5px] border-[#FFFFFF]' />
          <IconCircleFilled size={5} />
        </div>
        <span className='mt-2 block text-[10px]'>{breakpoint2.toFixed(2)}</span>
      </div>
    </div>
  )
}
