import { LoginResponse, User } from '@/models/user.model'
import {
  isLoggedInAtom,
  latestTradeDateAtom,
  refreshTokenAtom,
  sessionExpired,
  userAtom,
} from '@/store/store'
// import { tokenStore } from '@/store/token'
import { useAtom } from 'jotai'

export const useAuth = () => {
  const [user, setUser] = useAtom(userAtom)
  const [latestTradeDate, setLatestTradeDate] = useAtom(latestTradeDateAtom)
  const [isLoggedIn] = useAtom(isLoggedInAtom)
  const [refreshToken, setRefreshToken] = useAtom(refreshTokenAtom)
  const [isSessionExpired, setIsSessionExpired] = useAtom(sessionExpired)

  const login = (loginData: LoginResponse) => {
    setUser(loginData.data.user)
  }

  const logout = () => {
    setUser(null)
  }

  const setUserData = (user: User) => {
    setUser(user)
  }

  const setLatestTradeDateData = (date: number) => {
    setLatestTradeDate(date)
  }

  return {
    user,
    latestTradeDate,
    isLoggedIn,
    login,
    logout,
    refreshToken,
    setUserData,
    setLatestTradeDateData,
    setRefreshToken,
    isSessionExpired,
    setIsSessionExpired,
  }
}
