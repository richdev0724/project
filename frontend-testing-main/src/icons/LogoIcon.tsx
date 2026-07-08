import { DetailedHTMLProps, ImgHTMLAttributes } from 'react'
import marketMonoLogo from './MarketMonoWhite.png'

type LogoIconProps = DetailedHTMLProps<
  ImgHTMLAttributes<HTMLImageElement>,
  HTMLImageElement
>

const LogoIcon = (props: LogoIconProps) => (
  <img src={marketMonoLogo} alt='MarketMono' {...props} />
)

export default LogoIcon
