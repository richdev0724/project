interface ChartLayoutProps {
  className?: string
  children: React.ReactNode
  background?: string
  honeycombPattern?: string
  honeycombSize?: string
  shadow?: string
  overflow?: string
}

export default function ChartLayout({
  className,
  children,
  background,
  honeycombPattern,
  honeycombSize,
  shadow,
  overflow,
}: ChartLayoutProps) {
  const defaultShadow =
    '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'

  const style: React.CSSProperties = {
    background: honeycombPattern
      ? `url(${honeycombPattern}), ${background || 'var(--chart-background)'}`
      : background || 'var(--chart-background)',
    backgroundSize: honeycombPattern
      ? honeycombSize
        ? `${honeycombSize}, cover`
        : '80% 80%, cover'
      : 'cover',
    backgroundRepeat: honeycombPattern ? 'no-repeat, no-repeat' : 'no-repeat',
    backgroundPosition: honeycombPattern
      ? 'center center, center center'
      : 'center center',
    boxShadow: shadow || defaultShadow,
  }

  return (
    <div
      className={`relative min-h-[300px] w-full rounded-md p-5 ${overflow || ''} ${className || ''}`}
      style={style}
    >
      {children}
    </div>
  )
}
