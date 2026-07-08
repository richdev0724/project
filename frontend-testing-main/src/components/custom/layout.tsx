import * as React from 'react'
import { cn } from '@/lib/utils'

const LayoutContext = React.createContext<{
  offset: number
  fixed: boolean
} | null>(null)

interface LayoutProps extends React.HTMLAttributes<HTMLDivElement> {
  fixed?: boolean
}

const Layout = ({ className, fixed = false, ...props }: LayoutProps) => {
  const divRef = React.useRef<HTMLDivElement>(null)
  const [offset, setOffset] = React.useState(0)

  React.useEffect(() => {
    //TODO: make this token dynamic once api available
    const div = divRef.current

    if (!div) return
    const onScroll = () => setOffset(div.scrollTop)

    // clean up code
    div.removeEventListener('scroll', onScroll)
    div.addEventListener('scroll', onScroll, { passive: true })
    return () => div.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <LayoutContext.Provider value={{ offset, fixed }}>
      <div
        ref={divRef}
        data-layout='layout'
        className={cn(
          'h-full overflow-auto ',
          fixed && 'flex flex-col',
          className
        )}
        {...props}
      />
    </LayoutContext.Provider>
  )
}
Layout.displayName = 'Layout'

interface HeaderProps extends React.HTMLAttributes<HTMLDivElement> {
  sticky?: boolean
}

const Header = React.forwardRef<HTMLDivElement, HeaderProps>(
  ({ className, sticky, ...props }, ref) => {
    // Check if Layout.Header is used within Layout
    const contextVal = React.useContext(LayoutContext)
    if (contextVal === null) {
      throw new Error(
        `Layout.Header must be used within ${Layout.displayName}.`
      )
    }

    return (
      <div
        ref={ref}
        data-layout='header'
        className={cn(
          `z-10 flex h-[var(--header-height)] items-center gap-4 border-b-[2px] bg-background`,
          contextVal.offset > 10 && sticky ? 'shadow' : 'shadow-none',
          contextVal.fixed && 'flex-none',
          sticky && 'sticky top-0',
          className
        )}
        {...props}
      />
    )
  }
)
Header.displayName = 'Header'

const Body = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => {
  // Check if Layout.Body is used within Layout
  const contextVal = React.useContext(LayoutContext)
  if (contextVal === null) {
    throw new Error(`Layout.Body must be used within ${Layout.displayName}.`)
  }

  return (
    <div
      ref={ref}
      data-layout='body'
      className={cn(
        'px-0 py-0 md:px-4',
        contextVal && contextVal.fixed && 'flex-1',
        className
      )}
      {...props}
    />
  )
})
Body.displayName = 'Body'

const Header2 = React.forwardRef<HTMLDivElement, HeaderProps>(
  ({ className, sticky, ...props }, ref) => {
    // Check if Layout.Header2 is used within Layout
    const contextVal = React.useContext(LayoutContext)
    if (contextVal === null) {
      throw new Error(
        `Layout.Header2 must be used within ${Layout.displayName}.`
      )
    }

    return (
      <div
        ref={ref}
        data-layout='header2'
        className={cn(
          // `flex items-center justify-between border-b-[2px] py-2 md:px-5`,
          `z-30 flex h-[var(--header-height)] items-center justify-between border-b border-[#424242] md:px-6`,
          'bg-[#17181D]',
          contextVal.offset > 10 && sticky ? 'shadow' : 'shadow-none',
          contextVal.fixed && 'flex-none',
          sticky && 'sticky top-0',
          className
        )}
        {...props}
      />
    )
  }
)

const Footer = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => {
  // Check if Layout.Footer is used within Layout
  const contextVal = React.useContext(LayoutContext)
  if (contextVal === null) {
    throw new Error(`Layout.Footer must be used within ${Layout.displayName}.`)
  }

  return (
    <div
      ref={ref}
      data-layout='footer'
      className={cn(
        'z-10 flex h-[var(--footer-height)] items-center gap-4 border-t-[1px] border-t-muted bg-background md:px-8',
        contextVal.offset > 10 ? 'shadow' : 'shadow-none',
        contextVal.fixed && 'flex-none',
        className
      )}
      {...props}
    />
  )
})

Layout.Header = Header
Layout.Header2 = Header2
Layout.Body = Body
Layout.Footer = Footer
export { Layout }
