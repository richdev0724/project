import LogoText from '@/components/logo-text'
import { Link } from 'react-router-dom'

export default function TermsOfService() {
  return (
    <div className='min-h-screen overflow-y-auto bg-gradientRadial bg-cover bg-center bg-no-repeat'>
      <div className='flex min-h-screen items-center justify-center p-4'>
        <div
          className='w-full max-w-2xl rounded-xl border border-white/10 bg-[#1F2026]/40 shadow-2xl backdrop-blur-xl'
          style={{
            background:
              'linear-gradient(135deg, rgba(31, 32, 38, 0.4) 0%, rgba(31, 32, 38, 0.2) 100%)',
            boxShadow:
              '0 20px 50px -12px rgba(0, 0, 0, 0.5), 0 8px 32px 0 rgba(0, 0, 0, 0.37), inset 0 1px 0 0 rgba(255, 255, 255, 0.05)',
          }}
        >
          <div className='p-6 text-[#F5F7F7]/80'>
            <div className='mb-5 flex items-center space-x-3'>
              <Link to='/#'>
                <LogoText size='sm' />
              </Link>
              <div>
                <h1 className='text-2xl font-bold text-[#F5F7F7]'>
                  Terms Of Service
                </h1>
                <p className='text-xs text-[#F5F7F7]/70'>
                  Last updated: 06/10/2024
                </p>
              </div>
            </div>

            <div className='mb-5 h-px w-full bg-sky-500/50'></div>

            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Agreement to Terms
              </h1>
              <p className='mb-3 text-sm font-normal'>
                These Terms of Service ("Terms") constitute a legally binding
                agreement made between you, whether personally or on behalf of
                an entity ("you") and MarketMono LLC ("we," "us" or "our"),
                concerning your access to and use of the MarketMono website as
                well as any other media form, media channel, mobile website, or
                mobile application related, linked, or otherwise connected
                thereto (collectively, the "Site").
              </p>
              <p className='mb-3 text-sm font-normal'>
                By accessing the Site, you agree to be bound by these Terms. If
                you do not agree with all of these Terms, then you are expressly
                prohibited from using the Site and you must discontinue use
                immediately.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Changes to Terms
              </h1>
              <p className='mb-3 text-sm font-normal'>
                We reserve the right to make changes or modifications to these
                Terms at any time and for any reason. We will alert you about
                any changes by updating the "Last updated" date of these Terms,
                and you waive any right to receive specific notice of each such
                change. It is your responsibility to periodically review these
                Terms to stay informed of updates. You will be subject to, and
                will be deemed to have been made aware of and to have accepted,
                the changes in any revised Terms by your continued use of the
                Site after the date such revised Terms are posted.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Services and Subscriptions
              </h1>
              <p className='mb-3 text-sm font-normal'>
                MarketMono LLC provides various services and products
                (collectively, the "Services"). By registering for or using the
                Services, you agree to pay the applicable subscription fees and
                any other charges incurred in connection with your use of the
                Services. All fees are non-refundable except as required by law.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                {' '}
                User Responsibilities{' '}
              </h1>
              <h2 className='my-2 text-base font-semibold text-[#F5F7F7]'>
                {' '}
                You agree to:
              </h2>
              <ul className='list-inside list-disc text-sm'>
                <li>
                  Provide accurate, current, and complete information about
                  yourself as prompted by our registration forms.
                </li>
                <li>
                  Maintain the security of your password and identification.
                </li>
                <li>
                  Be responsible for all use of your account and for any actions
                  that take place using your account.
                </li>
              </ul>
              <h2 className='my-2 text-base font-semibold text-[#F5F7F7]'>
                {' '}
                You may not:{' '}
              </h2>
              <ul className='list-inside list-disc text-sm'>
                <li>Share your account or password with anyone.</li>
                <li>
                  Use automated systems or software to extract data from the
                  Site.
                </li>
                <li>
                  Copy, modify, reproduce, republish, distribute, or transmit
                  any of the website material for any commercial or other
                  purposes.
                </li>
              </ul>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                {' '}
                Intellectual Property Rights{' '}
              </h1>
              <p className='mb-3 text-sm font-normal'>
                Unless otherwise stated, MarketMono LLC and/or its licensors own
                the intellectual property rights for all material on the Site.
                All intellectual property rights are reserved. You may access
                this from the Site for your own personal use subjected to
                restrictions set in these Terms.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'> Disclaimer</h1>
              <p className='mb-3 text-sm font-normal'>
                There is a high degree of risk involved in trading stocks and
                options. Past results are not indicative of future returns.
                MarketMono LLC is not a registered investment advisor nor is
                licensed as such with any federal or state regulatory agency.
                Information provided and opinions expressed on the website do
                not constitute investment advice. Any and all ideas expressed,
                research, tutorials, and instructional resources are for
                educational purposes only.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                {' '}
                Limitation of Liability
              </h1>
              <p className='mb-3 text-sm font-normal'>
                In no event will MarketMono LLC, its affiliates, partners,
                members, managers, directors, officers, employees, or agents be
                liable to you or any third person for any indirect,
                consequential, exemplary, incidental, special, or punitive
                damages, including for any lost profits or lost data arising
                from your use of the Site, or any of the content or other
                materials on, accessed through, or downloaded from the Site,
                even if MarketMono LLC is aware or has been advised of the
                possibility of such damages. Notwithstanding anything to the
                contrary contained herein, the liability of MarketMono LLC to
                you for any cause whatsoever, and regardless of the form of the
                action, will at all times be limited to the amount paid, if any,
                by you to MarketMono LLC for the Site in the immediately
                preceding twelve months, but in no case will such liability to
                you exceed $1000.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'> Privacy </h1>
              <p className='mb-3 text-sm font-normal'>
                We respect your privacy rights. We will not sell or distribute
                your email address or other information without your express
                consent. Only MarketMono LLC, our agencies, attorneys, and
                partners are entitled to your information. For more information,
                please see the MarketMono LLC Privacy Policy.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                {' '}
                No Endorsement{' '}
              </h1>
              <p className='mb-3 text-sm font-normal'>
                You understand that neither MarketMono LLC, nor any part of the
                Service, or any equipment utilized by MarketMono LLC is
                sponsored or endorsed by any of the Information Providers.
                MarketMono LLC may contain links to websites operated by parties
                other than MarketMono LLC. Such links are provided for your
                convenience only. MarketMono LLC does not control such websites,
                and is not responsible for their contents. MarketMono LLC's
                inclusion of links to such websites does not imply any
                endorsement of the material on such websites or any association
                with their operators.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                {' '}
                Indemnification{' '}
              </h1>
              <p className='mb-3 text-sm font-normal'>
                You shall indemnify MarketMono LLC, the Information Providers,
                and each of their respective officers, directors, partners,
                employees, and agents (the "Indemnified Parties") against, and
                hold the Indemnified Parties harmless from, any and all claims
                or losses imposed on, incurred by, or asserted against the
                Indemnified Parties as a result of or relating to your
                noncompliance with any of the terms and conditions of this
                Agreement and your breach of any of your representations and
                warranties in this Agreement.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                {' '}
                Governing Law{' '}
              </h1>
              <p className='mb-3 text-sm font-normal'>
                These Terms shall be governed by and defined following the laws
                of Florida/United States. MarketMono LLC and yourself
                irrevocably consent that the courts of Florida/United States
                shall have exclusive jurisdiction to resolve any dispute which
                may arise in connection with these Terms.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Contact Information{' '}
              </h1>
              <p className='mb-3 text-sm font-normal'>
                If you have any questions about these Terms, please contact us
                at support@marketmono.com.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
