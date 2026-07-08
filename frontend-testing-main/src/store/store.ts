import { User } from '@/models/user.model'
import { atom, createStore } from 'jotai'
import { atomWithStorage } from 'jotai/utils'

export const userAtom = atom<User | null>(null)
export const latestTradeDateAtom = atom<number | null>(null)
export const isLoggedInAtom = atomWithStorage<boolean>('isLoggedIn', true)

export const refreshTokenAtom = atomWithStorage<string | null>(
  'refreshToken',
  null
)

export const isShowAudioPlayerAtom = atom<boolean>(false)

export const EmailValueAtom = atom<string>('')

export const showStepTwoAtom = atom<boolean>(false)

export const sessionExpired = atomWithStorage<boolean>(
  'isSessionExpired',
  false
)

export const isOptionsTradeLoadingAtom = atomWithStorage<boolean>(
  'isOptionsTradeLoading',
  true
)

export const isDarkPoolTradeLoadingAtom = atomWithStorage<boolean>(
  'isDarkPoolTradeLoading',
  true
)

export const isChimeNotificationOnAtom = atomWithStorage<boolean>(
  'isChimeNotificationOn',
  true
)

export const expandButtonPositionAtom = atomWithStorage<number>(
  'expandButtonPosition',
  90
)

export const isDarkPoolChimeNotificationOnAtom = atomWithStorage<boolean>(
  'isDarkPoolChimeNotificationOn',
  true
)

export const store = createStore()
export const setRefreshToken = (r: string) => {
  store.set(refreshTokenAtom, r)
}
