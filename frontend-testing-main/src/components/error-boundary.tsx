import { Component, ErrorInfo, ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
  errorInfo: ErrorInfo | null
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
    errorInfo: null,
  }

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error, errorInfo: null }
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({
      error,
      errorInfo,
    })
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div className='flex min-h-screen items-center justify-center bg-background p-4'>
          <div className='text-center'>
            <h1 className='mb-4 text-4xl font-bold text-primary'>
              Oops! Something went wrong
            </h1>
            <p className='mb-6 text-muted-foreground'>
              {this.state.error?.message || 'An unexpected error occurred'}
            </p>
            <div className='space-x-4'>
              <button
                onClick={() => window.location.reload()}
                className='rounded-md bg-primary px-4 py-2 text-primary-foreground hover:bg-primary/90'
              >
                Reload Page
              </button>
              <Link
                to='/'
                className='inline-block rounded-md bg-secondary px-4 py-2 text-secondary-foreground hover:bg-secondary/80'
              >
                Go Home
              </Link>
            </div>
            {process.env.NODE_ENV === 'development' && this.state.errorInfo && (
              <pre className='mt-6 max-h-[400px] overflow-auto rounded-lg bg-muted p-4 text-left text-sm'>
                <code>{this.state.errorInfo.componentStack}</code>
              </pre>
            )}
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
