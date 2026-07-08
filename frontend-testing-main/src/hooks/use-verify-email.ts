import { authService } from '@/api'
import { toast } from '@/components/ui/use-toast'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

/**
 * use verify email hook is used to verify email.
 * @returns
 */

type UseVerifyEmailProps = {
  token?: string
}

type UseVerifyEmailReturn = {
  isLoading: boolean
  isError: boolean
}

export const useVerifyEmail = ({
  token,
}: UseVerifyEmailProps): UseVerifyEmailReturn => {
  const [isLoading, setIsLoading] = useState<boolean>(false)
  const [isError, setIsError] = useState<boolean>(false) // New state for error
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: async (token: string) => {
      return authService.verifyEmail(token!)
    },
    onSuccess: async () => {
      setIsLoading(false)
      setTimeout(() => {
        navigate('/sign-in')
      }, 3000)
      toast({
        title: 'Email verified successfully',
      })
    },
    onError: () => {
      setIsLoading(false)
      setIsError(true)
    },
  })

  useEffect(() => {
    if (token) {
      const t = setTimeout(() => {
        setIsLoading(true)
        mutation.mutateAsync(token)
      }, 1000)
      return () => clearTimeout(t)
    }
  }, [token, mutation])

  return { isLoading, isError }
}
