import { isChimeNotificationOnAtom } from '@/store/store'
import { useAtom } from 'jotai'
import { useCallback, useEffect, useRef } from 'react'
import sound from '../../public/assets/images/sound/beep-06.mp3'

export const useSound = (audioUrl: string) => {
  const [isSoundOff] = useAtom(isChimeNotificationOnAtom)
  const audioRef = useRef<HTMLAudioElement | null>(null)

  useEffect(() => {
    audioRef.current = new Audio(sound)

    return () => {
      if (audioRef.current) {
        audioRef.current.pause()
        audioRef.current = null
      }
    }
  }, [audioUrl, isSoundOff])

  const playSound = useCallback(() => {
    if (audioRef.current && isSoundOff) {
      audioRef.current.currentTime = 0
      audioRef.current.play()
    }
  }, [isSoundOff])

  return { playSound }
}
