import AudioPlayer from '@/app/pages/options/components/audio-player'
import LogoutIcon from '@/assets/icons/logoutIcon'
import SettingsIcon from '@/assets/icons/settingsIcon'
import { SideLink } from '@/data/sidelinks'
import useCheckActiveNav from '@/hooks/use-check-active-nav'
import { useLogout } from '@/hooks/use-logout'
import { cn } from '@/lib/utils'
import { isShowAudioPlayerAtom } from '@/store/store'
import { IconChevronDown } from '@tabler/icons-react'
import { useAtom } from 'jotai'
import { cloneElement, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Button, buttonVariants } from './custom/button'
import SocialLinks from './social-links'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from './ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './ui/dropdown-menu'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from './ui/tooltip'

interface NavProps extends React.HTMLAttributes<HTMLDivElement> {
  isCollapsed: boolean
  links: SideLink[]
  closeNav: () => void
}

export default function Nav({
  links,
  isCollapsed,
  className,
  closeNav,
}: NavProps) {
  const navRef = useRef<HTMLElement>(null)
  const [indicatorStyle, setIndicatorStyle] = useState({
    top: 0,
    height: 0,
    opacity: 0,
  })
  const location = useLocation()

  useEffect(() => {
    if (!navRef.current) return

    const activeLink = navRef.current.querySelector('[aria-current="page"]')
    if (activeLink) {
      const navRect = navRef.current.getBoundingClientRect()
      const linkRect = activeLink.getBoundingClientRect()
      setIndicatorStyle({
        top: linkRect.top - navRect.top,
        height: linkRect.height,
        opacity: 1,
      })
    }
  }, [location.pathname])

  const renderLink = ({ sub, ...rest }: SideLink) => {
    const key = `${rest.title}-${rest.href}`
    if (isCollapsed && sub)
      return (
        <NavLinkIconDropdown
          {...rest}
          sub={sub}
          key={key}
          closeNav={closeNav}
        />
      )

    if (sub)
      return (
        <NavLinkDropdown {...rest} sub={sub} key={key} closeNav={closeNav} />
      )

    return (
      <NavLink
        {...rest}
        key={key}
        closeNav={closeNav}
        isNavOpen={isCollapsed}
      />
    )
  }

  const [showAudioPlayer] = useAtom(isShowAudioPlayerAtom)
  const { handleLogout } = useLogout()

  const additionalLinks = [
    { title: 'Settings', href: '/settings', icon: <SettingsIcon /> },
    {
      title: 'Logout',
      href: '#',
      icon: <LogoutIcon fill='#646568' />,
      onClick: handleLogout,
    },
  ]

  return (
    <div
      data-collapsed={isCollapsed}
      className={cn(
        'group border-b bg-background py-2  transition-all duration-300 md:border-none',
        className
      )}
      style={{
        willChange: 'max-width, opacity',
        transform: 'translateZ(0)',
      }}
    >
      <TooltipProvider delayDuration={0}>
        <nav ref={navRef} className='relative flex h-full flex-col'>
          {/* Sliding indicator */}
          <div
            className='pointer-events-none absolute right-0 w-[2px] rounded-l-[2px] bg-primary transition-all duration-300 ease-in-out'
            style={{
              top: `${indicatorStyle.top + 3}px`,
              height: `${indicatorStyle.height - 6}px`,
              opacity: indicatorStyle.opacity,
            }}
          />
          <div className='flex flex-col gap-5'>{links.map(renderLink)}</div>
          {showAudioPlayer && (
            <div className='flex justify-center'>
              <AudioPlayer isCollapsed={isCollapsed} />
            </div>
          )}
          <div className='mt-auto flex flex-col gap-[18px]'>
            {additionalLinks.map((link) => renderLink(link))}
          </div>
          <div className='h-12' />
          <SocialLinks isCollapsed={isCollapsed} />
        </nav>
      </TooltipProvider>
    </div>
  )
}

interface NavLinkProps extends SideLink {
  subLink?: boolean
  closeNav: () => void
  isNavOpen?: boolean
  onClick?: () => void
}

function NavLink({
  title,
  icon,
  href,
  closeNav,
  subLink = false,
  isNavOpen,
  onClick,
}: NavLinkProps) {
  const { checkActiveNav } = useCheckActiveNav()

  const handleClick = (e: React.MouseEvent) => {
    if (onClick) {
      e.preventDefault()
      onClick()
    }
    closeNav()
  }

  return (
    <Link
      to={href}
      onClick={handleClick}
      className={cn(
        buttonVariants({
          variant: checkActiveNav(href) ? 'secondary' : 'ghost',
          size: 'sm',
        }),
        'group/nav !flex h-6 items-center justify-start rounded-none px-[13px]',
        'relative text-sm font-normal',
        'hover:text-primary',
        'text-[#ffffff]',
        subLink && 'h-6 w-full border-l px-2'
      )}
      aria-current={checkActiveNav(href) ? 'page' : undefined}
    >
      <div
        className={cn(
          'mr-2 flex min-w-[20px] items-center justify-center',
          'transition-all duration-300 ease-in-out',
          'opacity-50',
          'group-hover/nav:opacity-100',
          checkActiveNav(href) && 'opacity-100'
        )}
      >
        {cloneElement(icon, {
          className: cn('[&>path]:fill-[#ffffff]', '[&>g]:stroke-[#ffffff]'),
          width: '14',
          height: '14',
        })}
      </div>
      <span
        className={cn(
          'translate-y-[1px] font-gothamLight text-[10px] leading-[14px] text-[#ffffff] transition-all duration-300 ease-in-out',
          'opacity-50',
          isNavOpen ? 'max-w-0 overflow-hidden opacity-0' : 'max-w-full',
          checkActiveNav(href) && 'opacity-100',
          'group-hover/nav:opacity-100'
        )}
        style={{
          willChange: 'opacity, max-width',
          backfaceVisibility: 'hidden',
        }}
      >
        {title}
      </span>
    </Link>
  )
}

function NavLinkDropdown({ title, icon, label, sub, closeNav }: NavLinkProps) {
  const { checkActiveNav } = useCheckActiveNav()

  /* Open collapsible by default
   * if one of child element is active */
  const isChildActive = !!sub?.find((s) => checkActiveNav(s.href))

  return (
    <Collapsible defaultOpen={isChildActive}>
      <CollapsibleTrigger
        className={cn(
          buttonVariants({ variant: 'ghost', size: 'sm' }),
          'group h-12 w-full justify-start rounded-none px-6'
        )}
      >
        <div className='mr-2'>{icon}</div>
        {title}
        {label && (
          <div className='ml-2 rounded-lg bg-primary px-1 text-[0.625rem] text-primary-foreground'>
            {label}
          </div>
        )}
        <span
          className={cn(
            'ml-auto transition-all group-data-[state="open"]:-rotate-180'
          )}
        >
          <IconChevronDown stroke={1} />
        </span>
      </CollapsibleTrigger>
      <CollapsibleContent className='collapsibleDropdown' asChild>
        <ul>
          {sub!.map((sublink) => (
            <li key={sublink.title} className='my-1 ml-8'>
              <NavLink {...sublink} subLink closeNav={closeNav} />
            </li>
          ))}
        </ul>
      </CollapsibleContent>
    </Collapsible>
  )
}

function NavLinkIconDropdown({ title, icon, label, sub }: NavLinkProps) {
  const { checkActiveNav } = useCheckActiveNav()

  /* Open collapsible by default
   * if one of child element is active */
  const isChildActive = !!sub?.find((s) => checkActiveNav(s.href))

  return (
    <DropdownMenu>
      <Tooltip delayDuration={0}>
        <TooltipTrigger asChild>
          <DropdownMenuTrigger asChild>
            <Button
              variant={isChildActive ? 'secondary' : 'ghost'}
              size='icon'
              className='h-12 w-12'
            >
              {icon}
            </Button>
          </DropdownMenuTrigger>
        </TooltipTrigger>
        <TooltipContent side='right' className='flex items-center gap-4'>
          {title}{' '}
          {label && (
            <span className='ml-auto text-muted-foreground'>{label}</span>
          )}
          <IconChevronDown
            size={18}
            className='-rotate-90 text-muted-foreground'
          />
        </TooltipContent>
      </Tooltip>
      <DropdownMenuContent side='right' align='start' sideOffset={4}>
        <DropdownMenuLabel>
          {title} {label ? `(${label})` : ''}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {sub!.map(({ title, icon, label, href }) => (
          <DropdownMenuItem key={`${title}-${href}`} asChild>
            <Link
              to={href}
              className={`${checkActiveNav(href) ? 'bg-secondary' : ''}`}
            >
              {icon} <span className='ml-2 max-w-52 text-wrap'>{title}</span>
              {label && <span className='ml-auto text-xs'>{label}</span>}
            </Link>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
