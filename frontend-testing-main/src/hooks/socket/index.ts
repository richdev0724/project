import { socket } from '@/utilities/socket'

type SocketEventHandler<T = any> = (data: T) => void

const useSocket = <TEvents extends string, TEmitEvents extends string>() => {
  const emit = <TData>(event: TEmitEvents, data: TData) => {
    socket.emit(event, data)
  }

  const on = <TData = any>(
    event: TEvents,
    callBackFn: SocketEventHandler<TData>
  ) => {
    socket.on(event.toString(), (e: TData) => {
      callBackFn(e)
    })
  }

  const off = (event: TEvents) => socket.off(event)

  const registerHandlers = <TData = any>(
    ...eventhandlers: [event: TEvents, callbackFn: SocketEventHandler<TData>][]
  ) => {
    eventhandlers.forEach((handler) => {
      const [event, callbackFn] = handler

      socket.on(event.toString(), (e: TData) => {
        callbackFn(e)
      })
    })

    return () => {
      const eventsToClear = eventhandlers.map((item) => item[0])
      eventsToClear.forEach((event) => socket.off(event))
    }
  }

  return {
    emit,
    on,
    off,
    active: socket.active,
    registerHandlers,
  }
}

export default useSocket
