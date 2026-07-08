import { Button } from '@/components/custom/button'
import Seo from '@/components/seo'
import { Separator } from '@/components/ui/separator'
import { useLoginUsingTwitter } from '@/hooks/use-login-using-twitter'
import LogoText from '@/components/logo-text'
import { Routes } from '@/utilities/routes'
import { motion } from 'motion/react'
import { useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import AuthLeftPart from './components/auth-left-part'
import GoogleAuthButton from './components/google-auth-button'
import { SignInForm } from './components/sign-in-form'

export default function SignIn() {
  const navigation = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const { mutation } = useLoginUsingTwitter()

  const hasCalledMutation = useRef(false)

  useEffect(() => {
    if (token && !hasCalledMutation.current) {
      hasCalledMutation.current = true
      mutation.mutateAsync(token)
    }
  }, [])

  const handleClickForgotPassword = () => {
    navigation('/forgot-password')
  }

  return (
    <>
      <Seo title='Login' />
      <div className='flex min-h-screen bg-gradientRadial bg-cover bg-center'>
        {/* Left side */}
        <AuthLeftPart />

        {/* Login Form */}
        <div className='flex w-1/2 items-center justify-center px-8 py-12'>
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className='w-full max-w-[480px] rounded-2xl bg-[#1F2026]/90 p-8 shadow-2xl backdrop-blur-sm'
          >
            {/* Tab Switcher */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.4, delay: 0.1 }}
              className='mb-8 flex justify-end'
            >
              <div className='inline-flex items-center rounded-lg bg-[#2D2E33] p-1'>
                <Button
                  variant='ghost'
                  className='rounded-md bg-[#4A4B50] px-6 py-2 text-sm font-medium text-[#F5F7F7] transition-colors hover:bg-[#4A4B50]/80'
                >
                  Sign In
                </Button>
                <Button
                  variant='ghost'
                  className='rounded-md px-6 py-2 text-sm font-medium text-[#F5F7F7] transition-colors hover:bg-[#3A3B40]'
                  onClick={() => navigation(Routes.SIGN_UP)}
                >
                  Register
                </Button>
              </div>
            </motion.div>

            {/* Logo and Welcome */}
            <div className='mb-8 flex flex-col items-center space-y-4'>
              <motion.div
                initial={{ scale: 0, rotate: -180 }}
                animate={{ scale: 1, rotate: 0 }}
                transition={{
                  type: 'spring',
                  stiffness: 260,
                  damping: 20,
                  delay: 0.2,
                }}
              >
                <LogoText size='md' />
              </motion.div>
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.3 }}
                className='text-center'
              >
                <h2 className='text-3xl font-semibold text-[#F5F7F7]'>
                  Welcome back
                </h2>
                <p className='mt-1 text-sm font-light text-[#F5F7F7]/70'>
                  we missed you!
                </p>
              </motion.div>
            </div>

            {/* Sign In Form */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.4 }}
              className='mb-6'
            >
              <SignInForm />
            </motion.div>

            {/* Divider */}
            <motion.div
              initial={{ opacity: 0, scaleX: 0 }}
              animate={{ opacity: 1, scaleX: 1 }}
              transition={{ duration: 0.5, delay: 0.5 }}
              className='mb-6 flex items-center'
            >
              <Separator className='flex-1 bg-gradient-to-r from-transparent via-gradientLinePrimary to-transparent' />
              <span className='px-3 text-sm font-normal text-[#F5F7F7]/70'>
                or sign in with
              </span>
              <Separator className='flex-1 bg-gradient-to-l from-transparent via-gradientLinePrimary to-transparent' />
            </motion.div>

            {/* Social Auth Buttons */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.5, delay: 0.6 }}
              className='mb-6 flex items-center justify-center'
            >
              <motion.div
                initial={{ opacity: 0, scale: 0 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{
                  type: 'spring',
                  stiffness: 260,
                  damping: 20,
                  delay: 0.7,
                }}
              >
                <GoogleAuthButton />
              </motion.div>
            </motion.div>

            {/* Forgot Password */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.4, delay: 0.85 }}
              className='mb-6 text-center'
            >
              <span
                className='cursor-pointer text-sm font-medium text-[#F5F7F7] transition-colors hover:text-primary'
                onClick={handleClickForgotPassword}
              >
                Forgot Password?
              </span>
            </motion.div>

            {/* Footer Links */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.4, delay: 0.9 }}
              className='flex items-center justify-center gap-4 text-xs text-[#F5F7F7]/60'
            >
              <span
                className='cursor-pointer transition-colors hover:text-[#F5F7F7]'
                onClick={() => navigation('/privacy-policy')}
              >
                Privacy Policy
              </span>
              <span>|</span>
              <span
                className='cursor-pointer transition-colors hover:text-[#F5F7F7]'
                onClick={() => navigation('/terms-of-service')}
              >
                Terms of Service
              </span>
            </motion.div>
          </motion.div>
        </div>
      </div>
    </>
  )
}
