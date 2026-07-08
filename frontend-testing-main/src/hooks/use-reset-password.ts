import { authService } from '@/api'
import { toast } from '@/components/ui/use-toast'
import { ResetPasswordApiRequest } from '@/models/user.model'
import {
  ResetPasswordRequest,
  resetPasswordSchema,
} from '@/validations/auth.validation'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useAuth } from './use-auth'

type UseResetPasswordProps = {
  token?: string
}

// type UseResetPasswordFormReturnType = {}

export const useResetPasswordForm = ({ token }: UseResetPasswordProps) => {
  const [isLoading, setIsLoading] = useState<boolean>(false)
  const [isError, setIsError] = useState<boolean>(false)
  const { login } = useAuth()

  const form = useForm<ResetPasswordRequest>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      newPassword: '',
      confirmNewPassword: '',
    },
    mode: 'onChange',
  })
  const isFormValid = form.formState.isValid

  const mutation = useMutation({
    mutationFn: (formData: ResetPasswordRequest) => {
      const apiData: ResetPasswordApiRequest = {
        password: formData.newPassword,
      }

      return authService.resetPassword(apiData, token!)
    },
    onSuccess: (response) => {
      login(response)
      setIsLoading(false)
    },
    onError: (error) => {
      setIsLoading(false)
      setIsError(true)
      toast({
        title: error?.message,
      })
    },
  })

  const onSubmit = async (data: ResetPasswordRequest) => {
    setIsLoading(true)
    mutation.mutateAsync(data)
  }

  return {
    form,
    isFormValid,
    onSubmit,
    isLoading,
    isError,
  }
}
