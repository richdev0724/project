import { Button } from '@/components/custom/button'
import Seo from '@/components/seo'
import LogoText from '@/components/logo-text'
import { IconArrowLeft } from '@tabler/icons-react'
import { motion } from 'motion/react'
import { useNavigate } from 'react-router-dom'
import { ForgotForm } from './components/forgot-form'
import AuthLeftPart from './components/auth-left-part'

export default function ForgotPassword() {
  const navigate = useNavigate()
  return (
    <>
      <Seo title='Forgot Password' />
      <div className='flex min-h-screen bg-gradientRadial bg-cover bg-center'>
        {/* Left side */}
        <AuthLeftPart />

        {/* Forgot Password Form */}
        <div className='flex w-1/2 items-center justify-center px-7 py-10'>
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{
              opacity: { duration: 0.5 },
            }}
            className='flex w-full max-w-[504px] flex-col rounded-xl border border-white/10 bg-[#1F2026]/40 px-7 pb-[42px] pt-[42px] shadow-2xl backdrop-blur-xl'
            style={{
              background:
                'linear-gradient(135deg, rgba(31, 32, 38, 0.4) 0%, rgba(31, 32, 38, 0.2) 100%)',
              boxShadow:
                '0 20px 50px -12px rgba(0, 0, 0, 0.5), 0 8px 32px 0 rgba(0, 0, 0, 0.37), inset 0 1px 0 0 rgba(255, 255, 255, 0.05)',
            }}
          >
            {/* Back Button */}
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.4, delay: 0.1 }}
              className='mb-7'
            >
              <Button
                size='icon'
                variant='ghost'
                className='text-[#F5F7F7] hover:bg-white/10'
                onClick={() => navigate(-1)}
              >
                <IconArrowLeft height={24} width={24} />
              </Button>
            </motion.div>

            {/* Logo and Content */}
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

              {/* Welcome Text */}
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.3 }}
                className='w-full space-y-2 text-center'
              >
                <h2 className='text-[1.7rem] font-semibold text-[#F5F7F7]'>
                  Reset your password
                </h2>
                <p className='text-xs font-light text-[#F5F7F7]/70'>
                  No worries. We'll send you reset instructions.
                </p>
              </motion.div>

              {/* Form */}
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, delay: 0.4 }}
                className='mx-auto w-full max-w-sm'
              >
                <ForgotForm />
              </motion.div>
            </div>
          </motion.div>
        </div>
      </div>
    </>
  )
}
