import { useLocation } from 'react-router-dom'

export default function useCheckActiveNav() {
  const { pathname } = useLocation()

  const checkActiveNav = (nav: string) => {
    const pathArray = pathname.split('/').filter((item) => item !== '')

    if (nav === '/' && pathArray.length < 1) return true

    const cleanNav = nav.replace(/^\//, '')

    // Check if it's an exact match first
    if (pathname === nav) return true

    // Special handling for ticker-overview routes
    if (
      nav.startsWith('/ticker-overview') &&
      pathname.startsWith('/ticker-overview')
    ) {
      return true
    }

    // Check if the pathname starts with the nav path (for dynamic routes)
    if (pathname.startsWith(nav) && cleanNav.includes('/')) return true

    // Check if any path segment matches (but exclude ticker-overview to prevent false positives)
    if (!nav.startsWith('/ticker-overview')) {
      return pathArray.includes(cleanNav)
    }

    return false
  }

  return { checkActiveNav }
}
