import { io } from 'socket.io-client'

// Get the backend URL based on environment
const getBackendUrl = () => {
  // If we're in development and not using ngrok, use localhost
  if (import.meta.env.DEV && !window.location.hostname.includes('ngrok')) {
    return 'http://localhost:3001'
  }

  // For ngrok or production, derive the backend URL
  const protocol = window.location.protocol
  const isNgrok = window.location.hostname.includes('ngrok')

  // If using ngrok, we need to use the same ngrok URL but with the backend port
  if (isNgrok) {
    // The ngrok URL already includes the correct protocol and hostname
    return window.location.origin.replace(':5173', ':3001')
  }

  // For other production environments, use the current host with backend port
  const host = window.location.hostname
  const port = '3001' // Your backend port

  return `${protocol}//${host}:${port}`
}

// Create socket connection to backend
export const socket = io(getBackendUrl(), {
  autoConnect: true,
  reconnection: true,
  reconnectionDelay: 1000,
  reconnectionAttempts: 5,
  timeout: 20000,
  transports: ['websocket', 'polling'],
})

// Connection event handlers with improved logging
socket.on('connect', () => {
  // Socket connected
})

socket.on('disconnect', () => {
  // Socket disconnected
})

socket.on('connect_error', () => {
  // Connection error occurred
})

socket.on('reconnect', () => {
  // Socket reconnected
})

socket.on('reconnect_error', () => {
  // Reconnection error occurred
})
