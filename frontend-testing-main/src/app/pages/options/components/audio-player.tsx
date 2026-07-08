import React, { useEffect, useRef, useState } from 'react'
import { Play, Pause, SkipBack, Square } from 'lucide-react'

import { CustomSlider } from '@/components/custom/custom-slider-audio-tab'
import { Range, Track } from '@radix-ui/react-slider'

interface AudioPlayerProps {
  isCollapsed: boolean
}

const CollapsibleAudioPlayer: React.FC<AudioPlayerProps> = ({
  isCollapsed,
}) => {
  // Player state
  const [isPlaying, setIsPlaying] = useState<boolean>(false)
  const [isPaused, setIsPaused] = useState<boolean>(false)
  const [currentTime, setCurrentTime] = useState<number>(0)
  const [duration, setDuration] = useState<number>(0)
  const [buttonClicked, setButtonClicked] = useState<
    'play' | 'pause' | 'skip' | 'stop'
  >('play')
  // Visualizer state
  const [isVisualizerInitialized, setIsVisualizerInitialized] =
    useState<boolean>(false)

  // Refs
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const miniCanvasRef = useRef<HTMLCanvasElement | null>(null)
  const fullCanvasRef = useRef<HTMLCanvasElement | null>(null)
  const animationRef = useRef<number | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)
  const analyserRef = useRef<AnalyserNode | null>(null)
  const dataArrayRef = useRef<Uint8Array | null>(null)
  const sourceRef = useRef<MediaElementAudioSourceNode | null>(null)

  // Create audio element outside of render to maintain consistent reference
  useEffect(() => {
    // Create audio element if it doesn't exist
    if (!audioRef.current) {
      const audio = new Audio('/Kalimba.mp3')
      audio.preload = 'metadata'

      // Set up event listeners
      audio.addEventListener('loadedmetadata', () => {
        setDuration(audio.duration)
      })

      audio.addEventListener('timeupdate', () => {
        setCurrentTime(audio.currentTime)
      })

      audio.addEventListener('ended', () => {
        setIsPlaying(false)
        setIsPaused(false)
        if (animationRef.current) {
          cancelAnimationFrame(animationRef.current)
          animationRef.current = null
        }
      })

      audio.addEventListener('play', () => {
        setIsPlaying(true)
        setIsPaused(false)
      })

      audio.addEventListener('pause', () => {
        setIsPlaying(false)
        setIsPaused(true)
      })

      audioRef.current = audio
    }

    // Cleanup on unmount
    return () => {
      const audio = audioRef.current
      if (audio) {
        audio.pause()
        audio.removeEventListener('loadedmetadata', () => {})
        audio.removeEventListener('timeupdate', () => {})
        audio.removeEventListener('ended', () => {})
        audio.removeEventListener('play', () => {})
        audio.removeEventListener('pause', () => {})
      }

      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current)
      }

      if (sourceRef.current) {
        sourceRef.current.disconnect()
      }

      if (
        audioContextRef.current &&
        audioContextRef.current.state !== 'closed'
      ) {
        audioContextRef.current.close()
      }
    }
  }, [])

  // Handle canvas size updates for mini player
  useEffect(() => {
    const resizeMiniCanvas = () => {
      const canvas = miniCanvasRef.current
      if (!canvas) return

      canvas.width = canvas.clientWidth
      canvas.height = canvas.clientHeight
    }

    resizeMiniCanvas()
    window.addEventListener('resize', resizeMiniCanvas)

    return () => window.removeEventListener('resize', resizeMiniCanvas)
  }, [])

  // Handle canvas size updates for full player
  useEffect(() => {
    const resizeFullCanvas = () => {
      const canvas = fullCanvasRef.current
      if (!canvas) return

      canvas.width = canvas.clientWidth
      canvas.height = canvas.clientHeight
    }

    resizeFullCanvas()
    window.addEventListener('resize', resizeFullCanvas)

    return () => window.removeEventListener('resize', resizeFullCanvas)
  }, [])

  // Initialize or update visualizer when playing state changes
  useEffect(() => {
    if (isPlaying) {
      if (!isVisualizerInitialized) {
        initializeVisualizer()
      } else {
        updateVisualizer()
      }
    } else if (animationRef.current) {
      cancelAnimationFrame(animationRef.current)
      animationRef.current = null
    }
  }, [isPlaying, isVisualizerInitialized])

  // Update visualizer when collapsed state changes
  useEffect(() => {
    // If playing and visualizer is initialized, update the visualizer
    if (isPlaying && isVisualizerInitialized) {
      // Small delay to ensure canvas has updated
      setTimeout(() => {
        if (animationRef.current) {
          cancelAnimationFrame(animationRef.current)
          animationRef.current = null
        }
        updateVisualizer()
      }, 300) // Longer delay to match the transition duration
    }
  }, [isCollapsed])

  const initializeVisualizer = () => {
    const audio = audioRef.current
    if (!audio) return

    try {
      // Create audio context and analyzer if they don't exist
      if (!audioContextRef.current) {
        audioContextRef.current = new (window.AudioContext ||
          (window as any).webkitAudioContext)()

        const analyser = audioContextRef.current.createAnalyser()
        analyser.fftSize = 256
        analyserRef.current = analyser

        const source = audioContextRef.current.createMediaElementSource(audio)
        sourceRef.current = source

        source.connect(analyser)
        analyser.connect(audioContextRef.current.destination)

        const bufferLength = analyser.frequencyBinCount
        dataArrayRef.current = new Uint8Array(bufferLength)

        setIsVisualizerInitialized(true)
      }

      updateVisualizer()
    } catch (error) {
      setIsVisualizerInitialized(false) // Fallback behavior
    }
  }

  const updateVisualizer = () => {
    if (!analyserRef.current || !dataArrayRef.current) return

    const activeCanvas = isCollapsed
      ? miniCanvasRef.current
      : fullCanvasRef.current
    if (!activeCanvas) return

    const ctx = activeCanvas.getContext('2d')
    if (!ctx) return

    const draw = () => {
      if (!isPlaying) return

      analyserRef.current!.getByteFrequencyData(dataArrayRef.current!)
      ctx.clearRect(0, 0, activeCanvas.width, activeCanvas.height)

      if (isCollapsed) {
        // Mini player visualization (60% width, centered)
        const visWidth = activeCanvas.width * 0.6
        const startX = (activeCanvas.width - visWidth) / 2
        const barWidth = (visWidth / dataArrayRef.current!.length) * 2

        for (let i = 0; i < dataArrayRef.current!.length; i++) {
          const barHeight =
            (dataArrayRef.current![i] / 255) * activeCanvas.height * 0.8
          const x = startX + i * barWidth

          if (x < startX + visWidth) {
            ctx.fillStyle = 'rgba(36, 212, 56, 1)'
            ctx.fillRect(
              x,
              activeCanvas.height - barHeight,
              barWidth - 1,
              barHeight
            )
          }
        }
      } else {
        // Full player visualization
        const barWidth = (activeCanvas.width / dataArrayRef.current!.length) * 2
        let x = 0

        for (let i = 0; i < dataArrayRef.current!.length; i++) {
          const barHeight =
            (dataArrayRef.current![i] / 255) * activeCanvas.height * 0.8
          ctx.fillStyle = 'rgba(36, 212, 56, 1)'
          ctx.fillRect(
            x,
            activeCanvas.height - barHeight,
            barWidth - 1,
            barHeight
          )
          x += barWidth
        }
      }

      animationRef.current = requestAnimationFrame(draw)
    }

    // Cancel any existing animation
    if (animationRef.current) {
      cancelAnimationFrame(animationRef.current)
      animationRef.current = null
    }

    animationRef.current = requestAnimationFrame(draw)
  }

  // Player controls
  const togglePlayPause = () => {
    const audio = audioRef.current
    if (!audio) return

    if (isPlaying) {
      audio.pause()
      setIsPaused(true)
    } else {
      setIsPlaying(true)
      setButtonClicked('play')
      audio.play().catch()
    }
  }

  const handlePlay = () => {
    const audio = audioRef.current
    if (!audio) return
    setButtonClicked('play')
    audio.play().catch()
  }

  const handlePause = () => {
    const audio = audioRef.current
    if (!audio) return
    setButtonClicked('pause')
    audio.pause()
  }

  const handleStop = () => {
    const audio = audioRef.current
    if (!audio) return
    setButtonClicked('stop')
    setIsPaused(false)
    audio.pause()
    audio.currentTime = 0
  }

  const handleSkipBack = () => {
    const audio = audioRef.current
    if (!audio) return
    setButtonClicked('skip')
    setIsPaused(false)
    audio.currentTime = 0
  }

  const handleSeek = (value: number[]) => {
    const audio = audioRef.current
    if (!audio) return

    audio.currentTime = value[0]
  }

  // Container style with transition
  const containerStyle = isCollapsed
    ? 'fixed transition-all duration-300 ease-in-out'
    : 'relative mx-2 w-44 rounded-xl bg-[#2D2D2D] p-2 text-white shadow-xl shadow-[#00000040] transition-all duration-300 ease-in-out'

  useEffect(() => {
    return () => {
      if (audioRef.current) {
        audioRef.current.play()
      }
    }
  }, [])

  // Render mini player (collapsed view)
  if (isCollapsed) {
    return (
      <div className='relative transition-all duration-300 ease-in-out'>
        <div className='flex h-9 w-9 flex-col items-center justify-center rounded-md border border-[#4A4848] bg-[#2D2D2D] transition-all duration-300 ease-in-out'>
          <button
            className='flex h-[18px] w-[18px] items-center justify-center rounded-full border border-[#000000] bg-[#28BF94] transition-colors duration-200'
            onClick={togglePlayPause}
          >
            {isPlaying ? (
              <Pause size={6} fill='#000000' stroke='#000000' />
            ) : (
              <Play size={6} fill='#000000' stroke='#000000' />
            )}
          </button>
          <div className='mx-auto mt-1 h-2 w-7 overflow-hidden rounded transition-all duration-300'>
            <canvas ref={miniCanvasRef} className='h-full w-full' />
          </div>
        </div>
      </div>
    )
  }

  // Render full player (expanded view)
  return (
    <div className={containerStyle}>
      <div className='rounded-xl border border-[#4A4848] bg-[#1A1B20] p-2 shadow-xl shadow-[#00000040] transition-all duration-300 ease-in-out'>
        <div className='flex items-center justify-between transition-opacity duration-300'>
          <h3 className='text-[7px] text-gray-300'>META Earnings Call</h3>
          <span className='text-[7px] text-green-400'>320kbps 44.1kHz</span>
        </div>

        <div
          className={`mt-2 text-end font-goodlyLight text-2xl font-thin text-white transition-all duration-300 ${isPaused ? 'animate-pulse' : ''}`}
        >
          {new Date(currentTime * 1000).toISOString().substring(14, 19)}
        </div>

        <CustomSlider
          className='thin-slider h-[1.5px] bg-[rgb(0,0,0)] transition-all duration-300'
          value={[currentTime]}
          onValueChange={handleSeek}
          max={duration || 100}
          step={0.1}
        >
          <Track>
            <Range className='h-[1.5px] bg-[#000000]' />
          </Track>
        </CustomSlider>

        <div className='mt-2 h-4 w-full overflow-hidden rounded transition-all duration-300'>
          <canvas ref={fullCanvasRef} className='h-full w-full' />
        </div>
      </div>

      <div className='my-2 flex items-center justify-center gap-2 transition-opacity duration-300'>
        <button
          className={`rounded-full border-x-[1px] border-y-[0.5px] border-[#000000] p-2 shadow-[inset_0_1px_1px_rgba(147,147,147,1)] ${buttonClicked === 'skip' && 'ring-1 ring-blue-600'} transition-transform duration-200 hover:scale-110`}
          onClick={handleSkipBack}
        >
          <SkipBack size={10} fill='#000000' color='#000000' />
        </button>
        <button
          className={`rounded-full border-x-[1px] border-y-[0.5px] border-[#000000] p-2 shadow-[inset_0_1px_1px_rgba(147,147,147,1)] ${buttonClicked === 'pause' && 'ring-1 ring-red-600'}  transition-transform duration-200 hover:scale-110`}
          onClick={handlePause}
        >
          <Pause size={10} fill='#000000' color='#000000' />
        </button>
        <button
          onClick={handlePlay}
          className={`rounded-full border-x-[1px] border-y-[0.5px] border-[#000000] p-2 shadow-[inset_0_1px_1px_rgba(147,147,147,1)] ${buttonClicked === 'play' && 'ring-1 ring-[#28BF94]'}  transition-transform duration-200 hover:scale-110`}
        >
          <Play size={10} fill='#000000' color='#000000' />
        </button>
        <button
          className={`rounded-full border-x-[1px] border-y-[0.5px] border-[#000000] p-2 shadow-[inset_0_1px_1px_rgba(147,147,147,1)] ${buttonClicked === 'stop' && 'ring-1 ring-red-600'} transition-transform duration-200 hover:scale-110`}
          onClick={handleStop}
        >
          <Square size={10} fill='#000000' color='#000000' />
        </button>
      </div>
    </div>
  )
}

export default CollapsibleAudioPlayer
