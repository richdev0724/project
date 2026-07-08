import { Button } from '@/components/custom/button'
import Seo from '@/components/seo'
import { useLoginUsingTwitter } from '@/hooks/use-login-using-twitter'
import LogoText from '@/components/logo-text'
import { Routes } from '@/utilities/routes'
import { motion, AnimatePresence } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import AuthLeftPart from './components/auth-left-part'
import GoogleAuthButton from './components/google-auth-button'
import { SignInForm } from './components/sign-in-form'
import { CreateAccountForm } from './components/create-account-from'
import EmailVerifyNotification from './components/email-verify-notification'
import { PrivacyPolicyModal } from './components/privacy-policy-modal'
import { TermsOfServiceModal } from './components/terms-of-service-modal'
import { ForgotForm } from './components/forgot-form'
import { IconArrowLeft } from '@tabler/icons-react'
import { showStepTwoAtom } from '@/store/store'
import { useAtom } from 'jotai'

export default function AuthPage() {
  const navigation = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const { mutation } = useLoginUsingTwitter()
  const [showStepTwo, setShowStepTwo] = useAtom<boolean>(showStepTwoAtom)
  const { state } = location
  const [showPrivacyPolicy, setShowPrivacyPolicy] = useState(false)
  const [showTermsOfService, setShowTermsOfService] = useState(false)
  const [showForgotPassword, setShowForgotPassword] = useState(false)

  const hasCalledMutation = useRef(false)

  // Determine which mode we're in based on the route
  const isSignUp = location.pathname === Routes.SIGN_UP
  const isSignIn = location.pathname === Routes.SIGN_IN
  const isForgotPassword = location.pathname === Routes.FORGOT_PASSWORD

  // Set forgot password state based on route
  useEffect(() => {
    if (isForgotPassword) {
      setShowForgotPassword(true)
    }
  }, [isForgotPassword])

  useEffect(() => {
    if (token && !hasCalledMutation.current) {
      hasCalledMutation.current = true
      mutation.mutateAsync(token)
    }
  }, [token, mutation])

  useEffect(() => {
    return () => {
      setShowStepTwo(false)
    }
  }, [setShowStepTwo])

  const handleClickForgotPassword = () => {
    setShowForgotPassword(true)
  }

  const handleBackFromForgotPassword = () => {
    setShowForgotPassword(false)
  }

  // Calculate height based on mode
  const containerHeight = showForgotPassword ? 520 : 766

  return (
    <>
      <Seo title={isSignUp ? 'Register' : 'Login'} />
      <div className='flex min-h-screen bg-gradientRadial bg-cover bg-center'>
        {/* Left side */}
        <AuthLeftPart />

        {/* Auth Form */}
        <div className='flex w-1/2 items-center justify-center px-7 py-10'>
          {!showStepTwo ? (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0, height: containerHeight }}
              transition={{
                opacity: { duration: 0.5 },
                height: {
                  type: 'spring',
                  stiffness: 300,
                  damping: 30,
                  delay: 0.3,
                },
              }}
              className='flex w-full max-w-[504px] flex-col overflow-hidden rounded-xl border border-white/10 bg-[#1F2026]/40 px-7 pb-[42px] pt-[42px] shadow-2xl backdrop-blur-xl'
              style={{
                background:
                  'linear-gradient(135deg, rgba(31, 32, 38, 0.4) 0%, rgba(31, 32, 38, 0.2) 100%)',
                boxShadow:
                  '0 20px 50px -12px rgba(0, 0, 0, 0.5), 0 8px 32px 0 rgba(0, 0, 0, 0.37), inset 0 1px 0 0 rgba(255, 255, 255, 0.05)',
              }}
            >
              <AnimatePresence mode='wait'>
                {showForgotPassword ? (
                  <motion.div
                    key='forgot-password'
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.3, ease: 'easeInOut' }}
                    className='flex h-full flex-col'
                  >
                    {/* Back Button */}
                    <div className='mb-7'>
                      <Button
                        size='icon'
                        variant='ghost'
                        className='text-[#F5F7F7] hover:bg-white/10'
                        onClick={handleBackFromForgotPassword}
                      >
                        <IconArrowLeft height={24} width={24} />
                      </Button>
                    </div>

                    {/* Logo and Content */}
                    <div className='mb-7 flex flex-col items-center space-y-3'>
                      <LogoText size='sm' />

                      {/* Welcome Text */}
                      <div className='w-full space-y-2 text-center'>
                        <h2 className='text-[1.7rem] font-semibold text-[#F5F7F7]'>
                          Reset your password
                        </h2>
                        <p className='text-xs font-light text-[#F5F7F7]/70'>
                          No worries. We'll send you reset instructions.
                        </p>
                      </div>

                      {/* Form */}
                      <div className='mx-auto w-full max-w-sm'>
                        <ForgotForm />
                      </div>
                    </div>
                  </motion.div>
                ) : (
                  <motion.div
                    key='auth'
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.3, ease: 'easeInOut' }}
                    className='flex h-full flex-col'
                  >
                    {/* Tab Switcher */}
                    <motion.div
                      initial={{ opacity: 0, x: 20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ duration: 0.4, delay: 0.1 }}
                      className='mb-7 flex justify-end'
                    >
                      <div
                        className='relative inline-flex items-center rounded-md bg-[#2D2E33] p-1'
                        style={{
                          boxShadow: '0 4px 12px rgba(0, 0, 0, 0.3)',
                        }}
                      >
                        <Button
                          variant='ghost'
                          className='relative z-10 rounded px-5 py-1.5 text-sm font-medium text-[#F5F7F7] transition-colors hover:bg-transparent'
                          onClick={() => navigation(Routes.SIGN_IN)}
                        >
                          {isSignIn && (
                            <motion.div
                              layoutId='activeTab'
                              className='absolute inset-0 rounded border border-white/10 bg-[#4A4B50]/40 backdrop-blur-md'
                              style={{
                                background:
                                  'linear-gradient(135deg, rgba(74, 75, 80, 0.4) 0%, rgba(74, 75, 80, 0.2) 100%)',
                                boxShadow:
                                  '0 4px 16px 0 rgba(0, 0, 0, 0.3), inset 0 1px 0 0 rgba(255, 255, 255, 0.05)',
                              }}
                              transition={{
                                type: 'spring',
                                stiffness: 380,
                                damping: 30,
                              }}
                            />
                          )}
                          <span className='relative z-10'>Sign In</span>
                        </Button>
                        <Button
                          variant='ghost'
                          className='relative z-10 rounded px-5 py-1.5 text-sm font-medium text-[#F5F7F7] transition-colors hover:bg-transparent'
                          onClick={() => navigation(Routes.SIGN_UP)}
                        >
                          {isSignUp && (
                            <motion.div
                              layoutId='activeTab'
                              className='absolute inset-0 rounded border border-white/10 bg-[#4A4B50]/40 backdrop-blur-md'
                              style={{
                                background:
                                  'linear-gradient(135deg, rgba(74, 75, 80, 0.4) 0%, rgba(74, 75, 80, 0.2) 100%)',
                                boxShadow:
                                  '0 4px 16px 0 rgba(0, 0, 0, 0.3), inset 0 1px 0 0 rgba(255, 255, 255, 0.05)',
                              }}
                              transition={{
                                type: 'spring',
                                stiffness: 380,
                                damping: 30,
                              }}
                            />
                          )}
                          <span className='relative z-10'>Register</span>
                        </Button>
                      </div>
                    </motion.div>

                    {/* Logo */}
                    <div className='mb-7 flex flex-col items-center space-y-3'>
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
                        <LogoText size='sm' />
                      </motion.div>

                      {/* Content that crossfades */}
                      <div className='w-full flex-grow'>
                        <AnimatePresence mode='wait'>
                          <motion.div
                            key={isSignUp ? 'signup' : 'signin'}
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            exit={{ opacity: 0 }}
                            transition={{
                              duration: 0.25,
                              ease: [0.4, 0, 0.2, 1],
                            }}
                            className='w-full'
                          >
                            {/* Welcome Text */}
                            <div className='mb-7 text-center'>
                              {isSignUp ? (
                                state?.fromLogin ? (
                                  <>
                                    <h2 className='text-[1.7rem] font-semibold text-[#F5F7F7]'>
                                      Looks like you don't
                                    </h2>
                                    <h2 className='text-[1.7rem] font-semibold text-[#F5F7F7]'>
                                      have an account yet.
                                    </h2>
                                    <p className='mt-1 text-xs font-light text-[#F5F7F7]/70'>
                                      we can fix that!
                                    </p>
                                  </>
                                ) : (
                                  <>
                                    <h2 className='text-[1.7rem] font-semibold text-[#F5F7F7]'>
                                      Create an account
                                    </h2>
                                    <p className='mt-1 text-xs font-light text-[#F5F7F7]/70'>
                                      join the family!
                                    </p>
                                  </>
                                )
                              ) : (
                                <>
                                  <h2 className='text-[1.7rem] font-semibold text-[#F5F7F7]'>
                                    Welcome back
                                  </h2>
                                  <p className='mt-1 text-xs font-light text-[#F5F7F7]/70'>
                                    we missed you!
                                  </p>
                                </>
                              )}
                            </div>

                            {/* Form */}
                            <div className='mb-5'>
                              {isSignUp ? (
                                <CreateAccountForm
                                  setShowStepTwo={setShowStepTwo}
                                />
                              ) : (
                                <SignInForm
                                  onForgotPassword={handleClickForgotPassword}
                                />
                              )}
                            </div>

                            {/* Divider */}
                            <div className='mb-5 text-center'>
                              <span className='text-xs font-normal text-[#F5F7F7]/70'>
                                {isSignUp
                                  ? 'or create an account with'
                                  : 'or sign in with'}
                              </span>
                            </div>

                            {/* Google Auth Button */}
                            <div className='mb-5 flex items-center justify-center'>
                              <GoogleAuthButton isSignUp={isSignUp} />
                            </div>

                            {/* Forgot Password */}
                            {isSignIn && (
                              <div className='mb-5 text-center'>
                                <span
                                  className='cursor-pointer text-xs font-medium text-[#F5F7F7] transition-colors hover:text-primary'
                                  onClick={handleClickForgotPassword}
                                >
                                  Forgot Password?
                                </span>
                              </div>
                            )}

                            {/* Spacer for Register to match Sign In height */}
                            {isSignUp && <div className='mb-5 h-4' />}
                          </motion.div>
                        </AnimatePresence>
                      </div>
                    </div>

                    {/* Footer Links - Static, doesn't crossfade */}
                    <div className='mt-auto flex items-center justify-center gap-4 text-xs text-[#F5F7F7]/60'>
                      <span
                        className='cursor-pointer transition-colors hover:text-[#F5F7F7]'
                        onClick={() => setShowPrivacyPolicy(true)}
                      >
                        Privacy Policy
                      </span>
                      <span>|</span>
                      <span
                        className='cursor-pointer transition-colors hover:text-[#F5F7F7]'
                        onClick={() => setShowTermsOfService(true)}
                      >
                        Terms of Service
                      </span>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          ) : (
            <EmailVerifyNotification />
          )}
        </div>
      </div>

      {/* Modals */}
      <PrivacyPolicyModal
        open={showPrivacyPolicy}
        onOpenChange={setShowPrivacyPolicy}
      />
      <TermsOfServiceModal
        open={showTermsOfService}
        onOpenChange={setShowTermsOfService}
      />
    </>
  )
}
