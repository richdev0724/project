// import { authService } from '@/api'
// import Loader from '@/components/loader'
// import { toast } from '@/components/ui/use-toast'
// import { useAuth } from '@/hooks/use-auth'
import { Routes } from '@/utilities/routes'
import React, { useMemo, lazy } from 'react'
import { RouterProvider, createBrowserRouter } from 'react-router-dom'
import { ErrorBoundary } from '@/components/error-boundary'
import GeneralError from './pages/errors/general-error'
import MaintenanceError from './pages/errors/maintenance-error'
import NotFoundError from './pages/errors/not-found-error'

// Wrap component with error boundary and suspense
const withErrorBoundary = (Component: React.ComponentType) => {
  return function WithErrorBoundary(props: any) {
    return (
      <ErrorBoundary>
        <React.Suspense
          fallback={
            <div className='flex h-screen items-center justify-center'>
              <div className='text-lg'>Loading...</div>
            </div>
          }
        >
          <Component {...props} />
        </React.Suspense>
      </ErrorBoundary>
    )
  }
}

// Lazy load components with error boundary
const AppShell = withErrorBoundary(lazy(() => import('@/components/app-shell')))
const AuthPage = withErrorBoundary(lazy(() => import('./pages/auth/auth-page')))
const VerifyEmail = withErrorBoundary(
  lazy(() => import('./pages/auth/verify-email'))
)
const ResetPassword = withErrorBoundary(
  lazy(() => import('./pages/auth/reset-password'))
)
const ForgotPassword = withErrorBoundary(
  lazy(() => import('./pages/auth/forgot-password'))
)
const OTP = withErrorBoundary(lazy(() => import('./pages/auth/otp')))
const TermsOfService = withErrorBoundary(
  lazy(() => import('./pages/terms-of-service'))
)
const PrivacyPolicy = withErrorBoundary(
  lazy(() => import('./pages/privacy-policy'))
)
const RefundPolicy = withErrorBoundary(
  lazy(() => import('./pages/refund-policy'))
)
const Dashboard = withErrorBoundary(lazy(() => import('./pages/dashboard')))
const Options = withErrorBoundary(lazy(() => import('./pages/options')))
const Flow = withErrorBoundary(lazy(() => import('./pages/flow')))
const Signals = withErrorBoundary(lazy(() => import('./pages/signals')))
const WhaleFlow = withErrorBoundary(lazy(() => import('./pages/whale-flow')))
const Alerts = withErrorBoundary(lazy(() => import('./pages/alerts')))
const Politicians = withErrorBoundary(lazy(() => import('./pages/politicians')))
const PoliticianTrades = withErrorBoundary(
  lazy(() => import('./pages/politician-trades'))
)
const Earnings = withErrorBoundary(lazy(() => import('./pages/earnings')))
const MonoNews = withErrorBoundary(lazy(() => import('./pages/mono-news')))
const Settings = withErrorBoundary(lazy(() => import('./pages/settings')))

const Setup = () => {
  const routers = useMemo(
    () =>
      createBrowserRouter(
        [
          // Auth routes
          {
            path: Routes.SIGN_IN,
            element: <AuthPage />,
            errorElement: <GeneralError />,
          },
          {
            path: Routes.SIGN_UP,
            element: <AuthPage />,
            errorElement: <GeneralError />,
          },
          {
            path: Routes.VERIFY_EMAIL,
            element: <VerifyEmail />,
            errorElement: <GeneralError />,
          },
          {
            path: Routes.RESET_PASSWORD,
            element: <ResetPassword />,
            errorElement: <GeneralError />,
          },
          {
            path: Routes.FORGOT_PASSWORD,
            element: <ForgotPassword />,
            errorElement: <GeneralError />,
          },
          {
            path: Routes.OTP,
            element: <OTP />,
            errorElement: <GeneralError />,
          },
          {
            path: Routes.TERMS_OF_SERVICE,
            element: <TermsOfService />,
            errorElement: <GeneralError />,
          },
          {
            path: Routes.PRIVACY_POLICY,
            element: <PrivacyPolicy />,
            errorElement: <GeneralError />,
          },
          {
            path: Routes.REFUND_POLICY,
            element: <RefundPolicy />,
            errorElement: <GeneralError />,
          },
          // Main routes
          {
            path: Routes.DASHBOARD,
            element: <AppShell />,
            errorElement: <GeneralError />,
            children: [
              {
                index: true,
                element: <Dashboard />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.OPTIONS,
                element: <Options />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.FLOW,
                element: <Flow />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.SIGNALS,
                element: <Signals />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.WHALE_FLOW,
                element: <WhaleFlow />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.ALERTS,
                element: <Alerts />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.POLITICIANS,
                element: <Politicians />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.POLITICIAN_TRADES,
                element: <PoliticianTrades />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.EARNINGS,
                element: <Earnings />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.MONO_NEWS,
                element: <MonoNews />,
                errorElement: <GeneralError />,
              },
              {
                path: Routes.SETTINGS,
                element: <Settings />,
                errorElement: <GeneralError />,
              },
            ],
          },
          // Error routes
          {
            path: Routes.ERROR.GENERAL,
            Component: GeneralError,
          },
          {
            path: Routes.ERROR.NOT_FOUND,
            Component: NotFoundError,
          },
          {
            path: Routes.ERROR.MAINTENANCE,
            Component: MaintenanceError,
          },
          { path: Routes.FALLBACK, Component: NotFoundError },
        ],
        {
          future: {
            v7_startTransition: true,
          },
        }
      ),
    []
  )

  return (
    <RouterProvider router={routers} future={{ v7_startTransition: true }} />
  )
}

const Router = () => {
  // Remove all user info fetching and loading logic
  return <Setup />
}

export default Router
