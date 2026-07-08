import React, { useCallback, useEffect, useRef, useState } from 'react'
import { DndProvider, useDrag, useDrop } from 'react-dnd'
import { HTML5Backend } from 'react-dnd-html5-backend'
import { Button } from './custom/button'
import TickerItem from './ticker-item'
import { Input } from './ui/input'

const ITEM_TYPE = 'TICKER'

interface DraggableTickerProps {
  ticker: string
  index: number
  moveTicker: (fromIndex: number, toIndex: number) => void
  onDelete: () => void
}

/**
 * DraggableTicker is a component that represents a single ticker item with drag-and-drop functionality.
 *
 * @param {string} ticker - The symbol of the ticker.
 * @param {number} index - The index of the ticker in the list.
 * @param {function} moveTicker - A function to handle moving the ticker from one position to another.
 * @param {function} onDelete - A function to handle the deletion of the ticker.
 *
 * The component uses the React DnD library to enable drag-and-drop support, allowing users to rearrange
 * ticker items by dragging them. It also highlights the ticker item when it is being hovered over during
 * a drag operation.
 */

const DraggableTicker = ({
  ticker,
  index,
  moveTicker,
  onDelete,
}: DraggableTickerProps) => {
  const [, drag] = useDrag({
    type: ITEM_TYPE,
    item: { index },
    collect: (monitor) => ({
      isDragging: monitor.isDragging(),
    }),
  })

  const [{ isOver }, drop] = useDrop({
    accept: ITEM_TYPE,
    hover: useCallback(
      (item: { index: number }) => {
        if (item.index !== index) {
          moveTicker(item.index, index)
          item.index = index
        }
      },
      [index, moveTicker]
    ),
    collect: (monitor) => ({
      isOver: monitor.isOver(),
    }),
  })

  const dragDropRef = useCallback(
    (node: HTMLDivElement | null) => {
      drag(node)
      drop(node)
    },
    [drag, drop]
  )

  return (
    <div
      ref={dragDropRef}
      className={`flex items-center space-x-2 rounded-md border px-4 py-2 text-white transition-all ${
        isOver ? 'bg-gray-700' : ''
      }`}
    >
      <TickerItem ticker={ticker} onDelete={onDelete} />
    </div>
  )
}

/**
 * The Ticker component displays a list of draggable ticker items. Users can drag the items to reorder
 * them. The component also includes a text input field to add new ticker items. The component stores the
 * list of tickers in local storage and loads them when the component is mounted.
 *
 * The component consists of a parent div with a flex layout, containing a child div with a flex layout
 * for the ticker items, and a child div for the input field. The ticker items are rendered as
 * DraggableTicker components, which are draggable and can be reordered. The input field is rendered as
 * an Input component, which is a bordered text input field with a placeholder and an add button.
 *
 * When the component is mounted, it loads the list of tickers from local storage and sets the initial
 * state of the component. When the user drags a ticker item, the component updates the state by moving
 * the item to the new position. When the user adds a new ticker item, the component updates the state by
 * adding the new item to the list.
 *
 * The component also handles the deletion of ticker items by removing them from the state when the user
 * clicks the delete button.
 */
const Ticker = () => {
  const defaultTickers = ['SPY', 'IWM', 'QQQ', 'TSLA', 'NVDA']
  const [tickers, setTickers] = useState<string[]>(() => {
    const savedTickers = localStorage.getItem('tickers')
    return savedTickers ? JSON.parse(savedTickers) : defaultTickers
  })
  const [newTicker, setNewTicker] = useState('')
  const [isInputVisible, setIsInputVisible] = useState(false)
  const inputContainerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    localStorage.setItem('tickers', JSON.stringify(tickers))
  }, [tickers])

  const moveTicker = useCallback((fromIndex: number, toIndex: number) => {
    setTickers((prevTickers) => {
      const updatedTickers = [...prevTickers]
      const [movedTicker] = updatedTickers.splice(fromIndex, 1)
      updatedTickers.splice(toIndex, 0, movedTicker)
      return updatedTickers
    })
  }, [])

  const handleAddTicker = () => {
    if (tickers.length >= 5) {
      alert('Maximum 5 widgets allowed.')
      return
    }

    const trimmedTicker = newTicker.trim().toUpperCase()
    if (!trimmedTicker) {
      alert('Please enter a valid ticker symbol.')
      return
    }

    setTickers((prevTickers) => [...prevTickers, trimmedTicker])
    setNewTicker('')
    setIsInputVisible(false)
  }

  const handleDeleteTicker = useCallback((index: number) => {
    setTickers((prevTickers) => prevTickers.filter((_, i) => i !== index))
  }, [])

  const toggleInputVisibility = () => {
    if (tickers.length < 5) {
      setIsInputVisible(true)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleAddTicker()
    }
  }

  return (
    <DndProvider backend={HTML5Backend}>
      <div className='flex items-center gap-x-4'>
        <div className='flex flex-row items-center space-x-4'>
          {tickers.map((ticker, index) => (
            <DraggableTicker
              key={`${ticker}-${index}`}
              ticker={ticker}
              index={index}
              moveTicker={moveTicker}
              onDelete={() => handleDeleteTicker(index)}
            />
          ))}
        </div>

        <div className='flex items-center' ref={inputContainerRef}>
          {!isInputVisible && (
            <Button
              className={`rounded-md bg-background px-1 text-2xl font-bold`}
              variant='ghost'
              disabled={tickers.length >= 5}
              onClick={toggleInputVisibility}
            >
              +
            </Button>
          )}
          {isInputVisible && (
            <div className='flex items-center space-x-2'>
              <Input
                className='h-10 border border-muted font-semibold placeholder:text-primary active:border-none'
                autoFocus
                placeholder='Enter ticker'
                value={newTicker}
                onChange={(e) => setNewTicker(e.target.value)}
                onKeyDown={handleKeyDown}
              />
              <Button
                className={`rounded-md bg-background px-1 text-2xl font-bold`}
                variant='ghost'
                onClick={handleAddTicker}
              >
                +
              </Button>
            </div>
          )}
        </div>
      </div>
    </DndProvider>
  )
}

export default Ticker
