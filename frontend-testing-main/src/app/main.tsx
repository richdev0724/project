import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from '@/components/ui/toaster'
import { ThemeProvider } from '@/components/theme-provider'
import '@/app/index.css'
import Router from './router'
import { HelmetProvider } from 'react-helmet-async'
import { GoogleOAuthProvider } from '@react-oauth/google'
import '@/services/background-scheduler' // Initialize background scheduler

const queryClient = new QueryClient()
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}>
      <HelmetProvider>
        <ThemeProvider defaultTheme='dark' storageKey='my-ui-theme'>
          <QueryClientProvider client={queryClient}>
            <Router />
            <Toaster />
          </QueryClientProvider>
        </ThemeProvider>
      </HelmetProvider>
    </GoogleOAuthProvider>
  </React.StrictMode>
)
