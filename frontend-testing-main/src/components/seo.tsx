import { Helmet } from 'react-helmet-async'

interface SeoProps {
  title: string
}

/**
 * Sets the SEO-related tags for the page using react-helmet-async.
 *
 * @param {object} props - Component props.
 * @param {string} props.title - The title for the page which will be appended to 'OnlyFlows'.
 */

export default function Seo({ title }: SeoProps) {
  const i = title ? `${title} | MarketMono` : 'MarketMono'
  return (
    <Helmet>
      <title>{i}</title>
      <meta content='MarketMono' name='description' />
      <meta content='© 2024 MarketMono. All Rights Reserved.' name='author' />

      <meta content='website' property='og:type' />
      <meta content={i} property='og:title' />
      <meta content='MarketMono' property='og:description' />
      <meta content='https://MarketMono.com' property='og:url' />
      <meta content='https://MarketMono.com/img/og.png' property='og:image' />

      <meta content='summary_large_image' name='twitter:card' />
      <meta content={i} name='twitter:title' />
      <meta content='MarketMono' name='twitter:description' />
      <meta content='https://MarketMono.com/img/og.png' name='twitter:image' />

      <meta
        content='OnlyFlows empowers your trading instincts with industry-grade data and essential utilities, providing the tools you need for successful trading.'
        name='keywords'
      ></meta>
    </Helmet>
  )
}
