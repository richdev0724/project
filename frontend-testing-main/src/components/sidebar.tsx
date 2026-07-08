import { sidelinks } from '@/data/sidelinks'
import LogoIcon from '@/icons/LogoIcon'
import { cn } from '@/lib/utils'
import { IconMenu2, IconX } from '@tabler/icons-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Button } from './custom/button'
import { Layout } from './custom/layout'
import Nav from './nav'
import { expandButtonPositionAtom } from '@/store/store'
import { useAtom } from 'jotai'

interface SidebarProps extends React.HTMLAttributes<HTMLElement> {
  isCollapsed: boolean
  setIsCollapsed: React.Dispatch<React.SetStateAction<boolean>>
}

export default function Sidebar({
  className,
  isCollapsed,
  setIsCollapsed,
}: SidebarProps) {
  const [navOpened, setNavOpened] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const [buttonPosition, setButtonPosition] = useAtom(expandButtonPositionAtom)

  const mouseStartYRef = useRef(0)
  const hasMoved = useRef(false)

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault()
      mouseStartYRef.current = e.clientY
      hasMoved.current = false

      const handleMouseMove = (e: MouseEvent) => {
        const deltaY = Math.abs(e.clientY - mouseStartYRef.current)

        if (deltaY > 5 && !hasMoved.current) {
          hasMoved.current = true
          setIsDragging(true)
        }

        if (isDragging || hasMoved.current) {
          const sidebar = document.querySelector('aside')
          if (!sidebar) return

          const sidebarRect = sidebar.getBoundingClientRect()
          const percentage =
            ((e.clientY - sidebarRect.top) / sidebarRect.height) * 100
          const clampedPercentage = Math.min(Math.max(percentage, 2), 98)
          setButtonPosition(clampedPercentage)
        }
      }

      const handleMouseUp = () => {
        window.removeEventListener('mousemove', handleMouseMove)
        window.removeEventListener('mouseup', handleMouseUp)

        if (!hasMoved.current) {
          setIsCollapsed((prev) => !prev)
        }

        setIsDragging(false)
      }

      window.addEventListener('mousemove', handleMouseMove)
      window.addEventListener('mouseup', handleMouseUp)
    },
    [isDragging, setIsCollapsed]
  )

  useEffect(() => {
    if (navOpened) {
      document.body.classList.add('overflow-hidden')
    } else {
      document.body.classList.remove('overflow-hidden')
    }
  }, [navOpened])

  // Preload animation on mount to prevent first-time jank
  useEffect(() => {
    const sidebar = document.querySelector('aside')
    if (sidebar) {
      // Force a reflow to initialize GPU acceleration
      sidebar.getBoundingClientRect()
    }
  }, [])

  return (
    <aside
      className={cn(
        'fixed left-0 right-0 top-0 z-50 w-full border-r-[2px] shadow-custom transition-all duration-500 ease-in-out md:bottom-0 md:right-auto md:h-svh',
        isCollapsed ? 'md:w-11' : 'md:w-[180px]',
        className
      )}
      style={{
        willChange: 'width',
        transform: 'translateZ(0)',
      }}
    >
      {/* Overlay in mobile */}
      <div
        onClick={() => setNavOpened(false)}
        className={cn(
          'absolute inset-0 w-full transition-[opacity] delay-100 duration-300 md:hidden',
          navOpened ? 'h-svh opacity-50' : 'h-0 opacity-0'
        )}
      />

      <Layout fixed className={cn('flex flex-col', navOpened ? 'h-svh' : '')}>
        {/* Header */}
        <Layout.Header
          sticky
          className='z-50 flex justify-between border-none bg-navBarBg pt-1.5'
        >
          <div className={cn('flex w-full items-center justify-center gap-2')}>
            <LogoIcon
              className={cn(
                'h-8 w-8 transition-all duration-300',
                isCollapsed && 'h-5 w-5'
              )}
            />
          </div>

          {/* Toggle Button in mobile */}
          <Button
            variant='ghost'
            size='icon'
            className='md:hidden'
            aria-label='Toggle Navigation'
            aria-controls='sidebar-menu'
            aria-expanded={navOpened}
            onClick={() => setNavOpened((prev) => !prev)}
          >
            {navOpened ? <IconX /> : <IconMenu2 />}
          </Button>
        </Layout.Header>
        <div className='bg-navBarBg pb-20' />

        <Nav
          id='sidebar-menu'
          className={cn(
            'z-40 h-full flex-1 overflow-auto bg-navBarBg transition-all duration-300',
            navOpened ? 'max-h-screen' : 'max-h-0 py-0 md:max-h-screen md:py-2'
          )}
          closeNav={() => setNavOpened(false)}
          isCollapsed={isCollapsed}
          links={sidelinks}
        />

        <Button
          onMouseDown={handleMouseDown}
          className={cn(
            'group absolute -right-2 z-0 md:inline-flex',
            isDragging && 'bg-primary/10'
          )}
          style={{
            top: `${buttonPosition}%`,
            transform: 'translateY(-50%)',
            padding: '0',
            margin: '0',
            background: 'linear-gradient(to right, #1f2026 0%, #454856 70%)',
            lineHeight: 1,
            display: 'flex',
            alignItems: 'center',
            height: '28px',
            width: '9px',
            justifyContent: 'center',
            borderRadius: '0 4px 4px 0',
          }}
        >
          <svg
            xmlns='http://www.w3.org/2000/svg'
            width='8'
            height='14'
            viewBox='0 0 8 14'
            fill='white'
          >
            <circle cx='4' cy='2' r='1.25' />
            <circle
              cx='4'
              cy='7'
              r='1.25'
              className='transition-all duration-200 ease-in-out group-hover:translate-x-0.5'
            />
            <circle cx='4' cy='12' r='1.25' />
          </svg>
        </Button>
      </Layout>
    </aside>
  )
}
