export const BASE_URL = 'https://api.marketmono.com/api'
export const WORKER_BASE_URL = 'https://worker.marketmono.com/api'
export const TOGGLE_FILTERS_BAR = 'Filters'

// Logic constants
export const USER_SESSION_LENGTH = 10 * 365 * 24 * 60 * 60 // 10 years

// Text constants

// Auth
export const CREATE_ACCOUNT_BUTTON_TEXT = 'Create Account'
export const CREATE_ACCOUNT_TITLE = 'Create Account'
export const LOGIN_TITLE = 'Log in'
export const LOGIN_BUTTON_TEXT = 'Login'
export const FORGOT_PASSWORD_TITLE = 'Forgot password?'
export const FORGOT_PASSWORD_BUTTON = 'Send reset link'
export const HAVE_ACCOUNT = 'Already have an account?'
export const DO_NOT_HAVE_ACCOUNT = "Don't have an account?"
export const FORGOT_PASSWORD = 'Forgot password? '
export const PASSWORD_PLACEHOLDER = 'Create Password'
export const CONFIRM_PASSWORD_PLACEHOLDER = 'Confirm Password'
export const INVALID_CREDENTIALS_ERROR = 'Invalid credentials'
export const LOGIN_FORM_TITLE = 'Welcome Back!'
export const SIGN_UP_LINK_TEXT = 'Sign Up'
export const RESET_PASSWORD_LINK_TEXT = 'Reset'
export const VERIFICATION_LINK_EXPIRED = 'Verification link expired'
export const VERIFICATION_LINK_EXPIRED_MESSAGE =
  'This verification link has expired or is invalid.'
export const COMMON_ERROR = 'Something went wrong. Please contact support'
export const RESET_EMAIL_SENT =
  'Password reset link sent to your email. Please check your inbox'
export const RESET_PASSWORD_TITLE = 'Reset Password'
export const BACK_TO_LOGIN = 'Back to login?'

// Flow table filter
export const LENS_FILTER_BUTTON = 'Lens Filter'
export const FILTER_RESET_BUTTON = 'Reset'
export const FILTER_APPLY_BUTTON = 'Apply'
export const FLOW_FILTER_MAX_LABEL = 'Max'
export const FLOW_FILTER_MIN_LABEL = 'Min'
export const FLOW_FILTER_TITLE = 'Flow Filters'
export const FLOW_FILTER_TICKER_PLACEHOLDER = 'Filter Tickers'
export const FLOW_SEARCH_PLACEHOLDER = 'Search Ticker'
export const FLOW_FILTER_TICKER_LABEL = 'Ticker'
export const FLOW_FILTER_IGNORE_TICKER_PLACEHOLDER = 'Ignore Tickers'
export const FLOW_FILTER_IGNORE_TICKER_LABEL = 'Ignore Tickers'
export const FLOW_FILTER_DATE_RANGE_LABEL = 'Date Range'
export const FLOW_FILTER_EXPIRATION_DATE_RANGE_LABEL = 'Expiration Range'
export const FLOW_FILTER_TO = 'to'
export const FLOW_FILTER_PREMIUM_LABEL = 'Premium'
export const FLOW_FILTER_PREMIUM_PLACEHOLDER = '1000000'
export const FLOW_FILTER_DTE_RANGE_LABEL = 'DTE Range'
export const FLOW_FILTER_CONTRACT_FILTERS_LABEL = 'Contract Filters'
export const FLOW_FILTER_CONTRACT_FILTERS_CALLS_LABEL = 'Hide Calls'
export const FLOW_FILTER_CONTRACT_FILTERS_PUTS_LABEL = 'Hide Puts'
export const FLOW_FILTER_CONTRACT_FILTERS_ITM_LABEL = 'Hide ITM'
export const FLOW_FILTER_CONTRACT_FILTERS_OTM_LABEL = 'Hide OTM'
export const FLOW_FILTER_ORDER_TYPES_LABEL = 'Order Types'
export const FLOW_FILTER_ORDER_TYPES_SWEEPS_LABEL = 'Hide Sweeps'
export const FLOW_FILTER_ORDER_TYPES_SINGLE_LEG_LABEL = 'Hide Single Leg'
export const FLOW_FILTER_ORDER_TYPES_MULTI_LEG_LABEL = 'Hide Multi Leg'
export const FLOW_FILTER_ORDER_TYPES_FLOOR_LABEL = 'Hide Floor'
export const FLOW_FILTER_TODAYS_TRADES_ONLY_LABEL = "Today's Trades Only"
export const FLOW_FILTER_HIDE_NEUTRAL_TRADES_LABEL = 'Hide Neutral Trades'
export const NO_DATA_LABEL = 'No data found'
export const COLUMNS_TOGGLE_BUTTON = 'Columns'
export const COLUMNS_TOGGLE_HEADER = 'Toggle Columns'
export const TAKE_INPUT_LABEL = 'Take: '
export const EMPTY_CELL = '-'
export const FLOW_FILTER_SIDE_FILTERS_LABEL = 'Side Filters'
export const FLOW_FILTER_BELOW_BID_LABEL = 'Hide Below Bid'
export const FLOW_FILTER_ABOVE_ASK_LABEL = 'Hide Above Ask'
export const FLOW_FILTER_NEUTRAL_LABEL = 'Hide Neutral'
export const FLOW_FILTER_BOUGHT_LABEL = 'Hide Bought'
export const FLOW_FILTER_SOLD_LABEL = 'Hide Sold'
export const FLOW_FILTER_SIZE_FILTERS_LABEL = 'Size Filters'
export const FLOW_FILTER_MIN_SIZE_LABEL = 'Min Size'
export const FLOW_FILTER_MIN_VOLUME_LABEL = 'Min Volume'
export const FLOW_FILTER_MIN_SIZE_PLACEHOLDER = '0'
export const FLOW_FILTER_MIN_VOLUME_PLACEHOLDER = '10000'
export const FLOW_FILTER_SIZE_ABOVE_OI_LABEL = 'Show Size > OI only'
export const FLOW_FILTER_VOLUME_ABOVE_OI_LABEL = 'Show Volume > OI only'

// Styles
export const HEADER_HEIGHT = 60
export const DATA_GRID_HEADER_HEIGHT = 64
export const FOOTER_HEIGHT = 36

//Login
export const MESSAGE_CONSTANTS = [
  {
    message: 'Looks like you entered an incorrect password',
    retryCountMessage: '',
  },
  {
    message: 'Oops you did it again.',
    retryCountMessage: '3 tries remaining',
  },
  {
    message: 'Well dangit, did you forget?',
    retryCountMessage: '2 tries remaining',
  },
  {
    message: 'You got one shot left...',
    retryCountMessage: '1 try remaining',
  },
  {
    message: "Now you've gone and did it.",
    retryCountMessage: '',
  },
]

export const LINE_GRADIENT_CONSTANTS = [
  {
    id: 1,
    lineColor: 'firstGradient',
    stroke: '#3F713B',
    stroke2: '#FFE315',
    label: 'hsla(53, 100%, 54%, 0.25)',
    className:
      'bg-gradient-to-l from-tickerOneGradientPrimary  to-tickerOneGradientPrimaryTransparent',
  },
  {
    id: 2,
    lineColor: 'secondGradient',
    stroke: '#4597A1',
    stroke2: '#F6F1E5',
    label: 'hsla(42, 49%, 93%, 0.25)',
    className:
      'bg-gradient-to-l from-tickerTwoGradientPrimary to-tickerTwoGradientPrimaryTransparent',
  },
  {
    id: 3,
    lineColor: 'thirdGradient',
    stroke: '#AE1515',
    stroke2: '#FA3939',
    label: 'hsla(0, 95%, 60%, 0.25)',
    className:
      'bg-gradient-to-l from-tickerThreeGradientPrimary to-tickerThreeGradientPrimaryTransparent',
  },
  {
    id: 4,
    lineColor: 'fourthGradient',
    stroke: '#0A3651',
    stroke2: '#398492',
    label: 'hsla(189, 44%, 40%, 0.25)',
    className:
      'bg-gradient-to-l from-tickerFourGradientPrimary to-tickerFourGradientPrimaryTransparent',
  },
  {
    id: 5,
    lineColor: 'fifthGradient',
    stroke: '#EF510D',
    stroke2: '#FF8A59',
    label: 'hsla(18, 100%, 67%, 0.25)',
    className:
      'bg-gradient-to-l from-tickerFiveGradientPrimary to-tickerFiveGradientPrimaryTransparent',
  },
]

export const MAGNIFICENT_SEVEN_LINE_GRADIENT_CONSTANTS = [
  {
    id: 1,
    lineColor: 'nvdaGradient',
    stroke: '#45CA89',
    stroke2: '#45CA89',
    label: 'hsla(148, 50%, 53%, 0.25)',
    className: 'bg-gradient-to-l from-[#45CA89] to-[#45CA89]',
  },
  {
    id: 2,
    lineColor: 'metaGradient',
    stroke: '#0A70E3',
    stroke2: '#0A70E3',
    label: 'hsla(214, 86%, 47%, 0.25)',
    className: 'bg-gradient-to-l from-[#0A70E3] to-[#0A70E3]',
  },
  {
    id: 3,
    lineColor: 'msftGradient',
    stroke: '#87D1FF',
    stroke2: '#87D1FF',
    label: 'hsla(200, 100%, 76%, 0.25)',
    className: 'bg-gradient-to-l from-[#87D1FF] to-[#87D1FF]',
  },
  {
    id: 4,
    lineColor: 'aaplGradient',
    stroke: '#DD9DF7',
    stroke2: '#DD9DF7',
    label: 'hsla(278, 78%, 78%, 0.25)',
    className: 'bg-gradient-to-l from-[#DD9DF7] to-[#DD9DF7]',
  },
  {
    id: 5,
    lineColor: 'googlGradient',
    stroke: '#FBC664',
    stroke2: '#FBC664',
    label: 'hsla(41, 94%, 70%, 0.25)',
    className: 'bg-gradient-to-l from-[#FBC664] to-[#FBC664]',
  },
  {
    id: 6,
    lineColor: 'amznGradient',
    stroke: '#E76D3D',
    stroke2: '#E76D3D',
    label: 'hsla(12, 77%, 57%, 0.25)',
    className: 'bg-gradient-to-l from-[#E76D3D] to-[#E76D3D]',
  },
  {
    id: 7,
    lineColor: 'tslaGradient',
    stroke: '#DB3C3B',
    stroke2: '#DB3C3B',
    label: 'hsla(1, 71%, 54%, 0.25)',
    className: 'bg-gradient-to-l from-[#DB3C3B] to-[#DB3C3B]',
  },
]

export const TIMEFRAME = [
  {
    label: '1D',
    value: '1D',
  },
  {
    label: '5D',
    value: '5D',
  },
  {
    label: '1M',
    value: '1M',
  },
  {
    label: '3M',
    value: '3M',
  },
  {
    label: '6M',
    value: '6M',
  },
  {
    label: 'YTD',
    value: 'YTD',
  },
  {
    label: '1Y',
    value: '1Y',
  },
  {
    label: '5Y',
    value: '5Y',
  },
]

export const MONO_MATRIX_TIMEFRAME = [
  {
    label: 'Today',
    value: 'today',
  },
  {
    label: 'Weekly',
    value: 'weekly',
  },
]

export const MARKET_PREMIUMS_TIMEFRAME = [
  {
    label: '5D',
    value: 5,
  },
  {
    label: '10D',
    value: 10,
  },
  {
    label: '15D',
    value: 15,
  },
]

export const MARKET_PREMIUMS_TICKERS = [
  {
    label: 'SPY',
    value: 'SPY',
  },
  {
    label: 'QQQ',
    value: 'QQQ',
  },
  {
    label: 'IWM',
    value: 'IWM',
  },
  {
    label: 'DIA',
    value: 'DIA',
  },
  {
    label: 'VTI',
    value: 'VTI',
  },
]

export const GAMMA_EXPOSURE_TICKERS = [
  {
    label: 'SPY',
    value: 'SPY',
  },
  {
    label: 'QQQ',
    value: 'QQQ',
  },
  {
    label: 'IWM',
    value: 'IWM',
  },
  {
    label: 'DIA',
    value: 'DIA',
  },
  {
    label: 'VTI',
    value: 'VTI',
  },
]

export const SECTORS = [
  'Communication Services',
  'Consumer Discretionary',
  'Consumer Staples',
  'Energy',
  'Financials',
  'Healthcare',
  'Industrials',
  'Information Technology',
  'Materials',
  'Real Estate',
  'Utilities',
]

export const SECTOR_ETF_TICKERS = [
  'XLC', // Communication Services
  'XLY', // Consumer Discretionary
  'XLP', // Consumer Staples
  'XLE', // Energy
  'XLF', // Financials
  'XLV', // Healthcare
  'XLI', // Industrials
  'XLK', // Information Technology
  'XLB', // Materials
  'XLRE', // Real Estate
  'XLU', // Utilities
]
