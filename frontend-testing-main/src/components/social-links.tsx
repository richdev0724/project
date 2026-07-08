interface SocialLinksProps {
  isCollapsed: boolean
}

const SocialLinks = ({ isCollapsed }: SocialLinksProps) => {
  return (
    <div className={`flex flex-col ${isCollapsed ? 'hidden' : ''}`}>
      <div className='flex items-center justify-center space-x-6'></div>
    </div>
  )
}

export default SocialLinks
