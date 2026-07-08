import { authService } from '@/api'
import { toast } from '@/components/ui/use-toast'
import { Routes } from '@/utilities/routes'
import {
  ForgotPasswordRequest,
  forgotPasswordSchema,
} from '@/validations/auth.validation'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router-dom'

/**
 * use forget password form hook is used to handle forget password form and its validation.
 * @returns
 */

type UseForgetPasswordFormReturnType = {
  form: any
  isFormValid: boolean
  onSubmit: (data: ForgotPasswordRequest) => void
  isLoading: boolean
  handleResendOtpEmail: () => void
}

export const useForgetPassword = (): UseForgetPasswordFormReturnType => {
  const [isLoading, setIsLoading] = useState<boolean>(false)
  const navigate = useNavigate()
  const location = useLocation()
  const form = useForm<ForgotPasswordRequest>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      email: '',
    },
    mode: 'onChange',
  })

  const isFormValid = form.formState.isValid

  const resendOtpEmailMutation = useMutation({
    mutationFn: async (data: ForgotPasswordRequest) =>
      authService.forgetPassword(data),
    onSuccess: () => {
      toast({
        title: 'OTP sent successfully',
      })
    },
    onError: () => {
      setIsLoading(false)
    },
  })

  const mutation = useMutation({
    mutationFn: async (data: ForgotPasswordRequest) =>
      authService.forgetPassword(data),
    onSuccess: (response) => {
      if (response.status === 404) {
        navigate(Routes.SIGN_UP, { state: { fromLogin: true } })
      } else {
        setIsLoading(false)
        navigate(Routes.OTP, { state: { email: form.getValues('email') } })
      }
    },
    onError: () => {
      setIsLoading(false)
    },
  })

  const onSubmit = async (data: ForgotPasswordRequest) => {
    setIsLoading(true)
    mutation.mutateAsync(data)
  }

  const handleResendOtpEmail = () => {
    resendOtpEmailMutation.mutateAsync({
      email: location.state.email,
    })
  }

  return {
    form,
    isFormValid,
    onSubmit,
    isLoading,
    handleResendOtpEmail,
  }
}
